"""诊断 pip 平台标签与 Python 构建信息。"""
import sys, platform, sysconfig

print("python:", sys.version)
print("executable:", sys.executable)
print("platform:", platform.platform())
print("machine:", platform.machine())
print("arch bits:", platform.architecture())
print("tags:", sysconfig.get_platform())