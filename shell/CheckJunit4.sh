#!/bin/bash

# 如果没有传入路径，则提示用法并退出
if [ -z "$1" ]; then
  echo "Usage: $0 <directory>"
  exit 1
fi

# 这里的 $1 就是脚本运行时传入的目录参数
TARGET_DIR="$1"

# 开始处理
grep -rlE "org.junit.[A-Z]" "$TARGET_DIR" --include '*.java' | while IFS= read -r FILE; do
    # 在 sed 替换前先输出 FILE
  echo "$FILE"
done