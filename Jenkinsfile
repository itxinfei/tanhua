// ============================================================
// 心飞交友 CI/CD 流水线（Jenkins Declarative Pipeline）
// 前置：Jenkins 全局工具配置 jdk25 / maven39 / node22（见部署文档）
// 触发：Gitee Webhook 推送 master 分支
//
// 运行形态：
//   - 后台管理(xfjy-admin)、App(xfjy-app) 在本地电脑运行，
//     CI 中仅做构建校验，不部署产物。
//   - Java 接口服务(xfjy-server / xfjy-video-service)部署到服务器，
//     经 deploy/deploy-service.sh 完成 备份→停旧→换jar→重启→健康检查。
// ============================================================
pipeline {
    agent any

    tools {
        jdk 'jdk25'
        maven 'maven39'
        nodejs 'node22'
    }

    options {
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    parameters {
        booleanParam(
            name: 'DEPLOY',
            defaultValue: true,
            description: '是否部署 Java 接口服务到服务器（取消勾选则只构建不部署）'
        )
        string(
            name: 'DEPLOY_SERVICES',
            defaultValue: 'xfjy-server,xfjy-video-service',
            description: '要部署的服务名，多个用英文逗号分隔'
        )
    }

    environment {
        // 宿主机部署基路径（通过 SSH 在宿主机上直接操作）
        HOST_BASE_DIR = '/home/itxinfei/mytanhua'
        HOST_LOG_DIR  = '/home/itxinfei/logs'
        // CI 专用部署账号与密钥（Jenkins 容器内 /var/jenkins_home/.ssh/xfjy_deploy_key）
        DEPLOY_HOST   = '172.18.0.1'
        DEPLOY_USER   = 'itxinfei'
        DEPLOY_KEY    = '/var/jenkins_home/.ssh/xfjy_deploy_key'
        SSH_OPTS      = '-o StrictHostKeyChecking=no -o UserKnownHostsFile=/var/jenkins_home/.ssh/known_hosts -o ConnectTimeout=15 -o BatchMode=yes'
    }

    stages {

        stage('后端 Maven 编译') {
            steps {
                sh 'mvn -B -ntp compile -DskipTests'
            }
        }

        // 注意：管理后台与 App 均在本地电脑运行，CI 中仅做构建校验。
        // 因此这两阶段失败不阻断后端服务的打包与部署（catchError 兜底）。
        stage('管理后台构建') {
            steps {
                dir('xfjy-admin') {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                        sh 'rm -f package-lock.json && npm install --legacy-peer-deps --no-audit --no-fund'
                        sh 'npm install @rolldown/binding-linux-x64-gnu --no-save --no-audit --no-fund'
                        sh 'npm run build'
                    }
                }
            }
        }

        stage('App H5 构建') {
            steps {
                dir('xfjy-app') {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                        sh 'rm -f package-lock.json && npm install --legacy-peer-deps --no-audit --no-fund'
                        sh 'npm run build:h5'
                    }
                }
            }
        }

        // ---------- CD：后端打包 ----------
        stage('后端服务打包') {
            steps {
                echo "打包待部署的 Java 服务: ${params.DEPLOY_SERVICES}"
                script {
                    def services = params.DEPLOY_SERVICES.split(',').collect { it.trim() }.findAll { it }
                    if (!services) {
                        error('DEPLOY_SERVICES 为空，没有可打包的服务')
                    }
                    // 关键前置：application-local.yml 被 .gitignore 排除（防泄露本地密码），
                    // CI 检出代码里没有该文件。若某模块需要数据源（如 xfjy-server 用 MySQL），
                    // 直接打包会导致 jar 缺少 application-local.yml，启动报
                    //   Failed to configure a DataSource: 'url' attribute is not specified
                    // 判定依据：模块存在 application-test.yml 说明它确实需要 local 配置
                    // （仅用 MongoDB/Redis 的模块如 video-service 没有该文件，不需要注入）。
                    // 模板 deploy/application-local.ci.yml 中所有连接信息均为 ${ENV:默认值}，
                    // 由部署时的环境变量注入真实值，模板本身不含任何真实密码。
                    services.each { svc ->
                        sh """
                            echo '>>> preparing CI config for ${svc}'
                            RES='${svc}/src/main/resources'
                            if [ -d "\$RES" ] && [ -f "\$RES/application-test.yml" ]; then
                              if [ ! -f "\$RES/application-local.yml" ]; then
                                cp -f 'deploy/application-local.ci.yml' "\$RES/application-local.yml"
                                echo '    generated application-local.yml from deploy/application-local.ci.yml'
                              else
                                echo '    application-local.yml already exists'
                              fi
                              # aliyun.properties 同样被 gitignore，如缺失则用 example 模板占位
                              if [ ! -f "\$RES/aliyun.properties" ] && [ -f "\$RES/aliyun.properties.example" ]; then
                                cp -f "\$RES/aliyun.properties.example" "\$RES/aliyun.properties"
                                echo '    generated aliyun.properties from example template'
                              fi
                            else
                              echo '    module does not require local config, skip'
                            fi
                        """
                    }
                    // 逐个模块打包（-am 带上依赖模块，保证接口变更能打进 jar）
                    services.each { svc ->
                        sh """
                            echo '>>> packaging ${svc}'
                            mvn -B -ntp package -DskipTests -pl ${svc} -am
                            echo '>>> verifying ${svc} jar config'
                            JAR='${svc}/target/${svc}-1.0-SNAPSHOT.jar'
                            # 需要数据源的模块必须有 application-local.yml，否则部署必失败
                            if [ -f '${svc}/src/main/resources/application-test.yml' ]; then
                              unzip -l \$JAR | grep -q 'BOOT-INF/classes/application-local.yml' || \\
                                (echo 'ERROR: jar 缺少 application-local.yml，无法部署' && exit 1)
                              echo '    OK: application-local.yml 已打包'
                            else
                              echo '    module needs no local config'
                            fi
                        """
                    }
                }
            }
        }

        // ---------- CD：投递产物到宿主机 ----------
        stage('投递产物到服务器') {
            steps {
                script {
                    def services = params.DEPLOY_SERVICES.split(',').collect { it.trim() }.findAll { it }
                    // 通过 SSH + scp 把 workspace 中的 jar 投递到宿主机部署目录
                    services.each { svc ->
                        sh """
                            echo '>>> delivering ${svc}'
                            ssh ${env.SSH_OPTS} -i ${env.DEPLOY_KEY} ${env.DEPLOY_USER}@${env.DEPLOY_HOST} \
                              "mkdir -p ${env.HOST_BASE_DIR}/${svc}/target"
                            scp ${env.SSH_OPTS} -i ${env.DEPLOY_KEY} \
                              ${svc}/target/${svc}-1.0-SNAPSHOT.jar \
                              ${env.DEPLOY_USER}@${env.DEPLOY_HOST}:${env.HOST_BASE_DIR}/${svc}/target/${svc}-1.0-SNAPSHOT.jar
                            ssh ${env.SSH_OPTS} -i ${env.DEPLOY_KEY} ${env.DEPLOY_USER}@${env.DEPLOY_HOST} \
                              "ls -la ${env.HOST_BASE_DIR}/${svc}/target/${svc}-1.0-SNAPSHOT.jar"
                        """
                    }
                }
            }
        }

        // ---------- CD：重启服务并健康检查 ----------
        stage('重启服务并健康检查') {
            when {
                expression { return params.DEPLOY == true }
            }
            steps {
                script {
                    def services = params.DEPLOY_SERVICES.split(',').collect { it.trim() }.findAll { it }
                    // 通过 SSH 在宿主机上执行部署脚本（备份→停旧→换jar→setsid重启→健康检查）
                    services.each { svc ->
                        sh """
                            echo '>>> deploying ${svc}'
                            ssh ${env.SSH_OPTS} -i ${env.DEPLOY_KEY} ${env.DEPLOY_USER}@${env.DEPLOY_HOST} \
                              "cd ${env.HOST_BASE_DIR} && bash deploy/deploy-service.sh ${svc}"
                        """
                    }
                }
            }
        }
    }

    post {
        success { echo '全部构建与部署通过' }
        failure { echo '构建或部署失败，请查看上方日志定位' }
        always { deleteDir() }
    }
}
