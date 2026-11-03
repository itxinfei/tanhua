"""检查双塔训练依赖是否就绪（torch/pandas/numpy/pymongo/sklearn/requests）。"""
import importlib.metadata as m

REQUIRED = ["torch", "pandas", "numpy", "pymongo", "scikit-learn", "requests"]

missing = []
for pkg in REQUIRED:
    try:
        print(f"OK   {pkg} {m.version(pkg)}")
    except m.PackageNotFoundError:
        missing.append(pkg)
        print(f"MISS {pkg}")

if missing:
    raise SystemExit(f"缺少依赖: {', '.join(missing)}")
print("全部依赖就绪")