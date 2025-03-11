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
  sed -i '' \
    -e 's/org.junit.After;/org.junit.jupiter.api.AfterEach;/' \
    -e 's/@After$/@AfterEach/' \
    -e 's/org.junit.AfterClass;/org.junit.jupiter.api.AfterAll;/' \
    -e 's/@AfterClass$/@AfterAll/' \
    -e 's/org.junit.Before;/org.junit.jupiter.api.BeforeEach;/' \
    -e 's/@Before$/@BeforeEach/' \
    -e 's/org.junit.BeforeClass;/org.junit.jupiter.api.BeforeAll;/' \
    -e 's/@BeforeClass$/@BeforeAll/' \
    -e 's/org.junit.Test/org.junit.jupiter.api.Test/' \
    -e 's/org.junit.Assert/org.junit.jupiter.api.Assertions/' \
    -e 's/junit.framework.TestCase/org.junit.jupiter.api.Assertions/' \
    -e 's/Assert\./Assertions./' \
    -e 's/org.junit\.\*/org.junit.jupiter.api.\*/g' \
    -e 's/@Ignore$/@Disabled/' \
    "$FILE"
done