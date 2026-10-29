// ============================================================
// 心飞交友 CI 流水线（Jenkins Declarative Pipeline）
// 前置：Jenkins 全局工具配置 jdk25 / maven39 / node22（见部署文档）
// 触发：Gitee Webhook 推送 master 分支
// ============================================================
pipeline {
    agent any

    tools {
        jdk 'jdk25'
        maven 'maven39'
        nodejs 'node22'
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {

        stage('后端 Maven 编译') {
            steps {
                sh 'mvn -B -ntp compile -DskipTests'
            }
        }

        stage('管理后台构建') {
            steps {
                dir('xfjy-admin') {
                    sh 'npm install --no-audit --no-fund'
                    sh 'npm run build'
                }
            }
        }

        stage('App H5 构建') {
            steps {
                dir('xfjy-app') {
                    sh 'npm install --no-audit --no-fund'
                    sh 'npm run build:h5'
                }
            }
        }
    }

    post {
        success { echo '全部构建通过' }
        failure { echo '构建失败，请查看上方日志定位' }
        always { deleteDir() }
    }
}