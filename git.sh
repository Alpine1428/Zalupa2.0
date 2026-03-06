#!/bin/bash
# ============================================================
# git.sh — Загрузка проекта на GitHub и запуск компиляции
# Репозиторий: https://github.com/Alpine1428/Zalupa2.0
# ============================================================

set -e

REPO_URL="https://github.com/Alpine1428/Zalupa2.0.git"
BRANCH="main"

echo "========================================="
echo "  ZalupaReport — Git Push & CI Build"
echo "========================================="

# -----------------------------------------------------------
# 1. Создаём .github/workflows/build.yml для автокомпиляции
# -----------------------------------------------------------
echo "[1/6] Создаю GitHub Actions workflow..."

mkdir -p .github/workflows

cat > .github/workflows/build.yml << 'WORKFLOW_EOF'
name: Build Mod

on:
  push:
    branches: [ main, master ]
  pull_request:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle 8.8
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.8'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build with Gradle 8.8
        run: gradle wrapper --gradle-version 8.8 && ./gradlew build --no-daemon --stacktrace

      - name: Upload build artifacts
        uses: actions/upload-artifact@v4
        with:
          name: ZalupaReport-mod
          path: build/libs/*.jar
          retention-days: 30
          if-no-files-found: warn
WORKFLOW_EOF

echo "  -> .github/workflows/build.yml создан"

# -----------------------------------------------------------
# 2. Создаём .gitignore если его нет
# -----------------------------------------------------------
echo "[2/6] Проверяю .gitignore..."

if [ ! -f .gitignore ]; then
cat > .gitignore << 'GITIGNORE_EOF'
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# IDE
.idea/
*.iml
*.ipr
*.iws
.vscode/
*.code-workspace
out/

# OS
.DS_Store
Thumbs.db
desktop.ini

# Fabric
run/
logs/
*.log

# Build output
*.class
GITIGNORE_EOF
echo "  -> .gitignore создан"
else
echo "  -> .gitignore уже существует"
fi

# -----------------------------------------------------------
# 3. Создаём Gradle Wrapper если нет
# -----------------------------------------------------------
echo "[3/6] Проверяю Gradle Wrapper..."

if [ ! -f gradlew ]; then
    echo "  -> Gradle wrapper не найден, пытаюсь создать..."
    if command -v gradle &> /dev/null; then
        gradle wrapper --gradle-version 8.8
        echo "  -> Gradle wrapper создан (v8.8)"
    else
        echo "  -> ВНИМАНИЕ: gradle не найден локально."
        echo "     Wrapper будет создан на GitHub Actions."
        # Создаём минимальный gradlew для GitHub Actions
        mkdir -p gradle/wrapper
        
        # Скачиваем wrapper jar и properties
        echo "  -> Скачиваю gradle-wrapper.properties..."
        cat > gradle/wrapper/gradle-wrapper.properties << 'PROPS_EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.8-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
PROPS_EOF
        echo "  -> gradle-wrapper.properties создан"
    fi
else
    echo "  -> gradlew найден"
fi

# -----------------------------------------------------------
# 4. Инициализируем Git если нужно
# -----------------------------------------------------------
echo "[4/6] Настраиваю Git..."

if [ ! -d .git ]; then
    echo "  -> Инициализация git репозитория..."
    git init
    git branch -M "$BRANCH"
    echo "  -> Git инициализирован"
else
    echo "  -> Git репозиторий уже существует"
fi

# Проверяем/добавляем remote
if git remote get-url origin &> /dev/null; then
    CURRENT_URL=$(git remote get-url origin)
    if [ "$CURRENT_URL" != "$REPO_URL" ]; then
        echo "  -> Обновляю remote origin: $REPO_URL"
        git remote set-url origin "$REPO_URL"
    else
        echo "  -> Remote origin уже настроен"
    fi
else
    echo "  -> Добавляю remote origin: $REPO_URL"
    git remote add origin "$REPO_URL"
fi

# -----------------------------------------------------------
# 5. Коммитим все изменения
# -----------------------------------------------------------
echo "[5/6] Коммичу изменения..."

git add -A

# Проверяем есть ли что коммитить
if git diff --cached --quiet 2>/dev/null; then
    echo "  -> Нет новых изменений для коммита"
else
    TIMESTAMP=$(date "+%Y-%m-%d %H:%M:%S")
    COMMIT_MSG="Auto build: fix afk chat message + CI setup [$TIMESTAMP]"
    git commit -m "$COMMIT_MSG"
    echo "  -> Коммит создан: $COMMIT_MSG"
fi

# -----------------------------------------------------------
# 6. Пушим на GitHub
# -----------------------------------------------------------
echo "[6/6] Пушу на GitHub..."

# Пробуем запушить
if git push -u origin "$BRANCH" 2>&1; then
    echo ""
    echo "========================================="
    echo "  ГОТОВО!"
    echo "========================================="
    echo ""
    echo "  Код загружен на GitHub:"
    echo "  https://github.com/Alpine1428/Zalupa2.0"
    echo ""
    echo "  Компиляция автоматически запустится"
    echo "  в GitHub Actions. Смотри:"
    echo "  https://github.com/Alpine1428/Zalupa2.0/actions"
    echo ""
    echo "  Скомпилированный .jar будет доступен"
    echo "  в артифактах после успешной сборки."
    echo "========================================="
else
    echo ""
    echo "========================================="
    echo "  ОШИБКА PUSH!"
    echo "========================================="
    echo ""
    echo "  Возможные причины:"
    echo "  1. Нет доступа к репозиторию"
    echo "  2. Нужно настроить SSH/токен"
    echo ""
    echo "  Для HTTPS с токеном:"
    echo "    git remote set-url origin https://TOKEN@github.com/Alpine1428/Zalupa2.0.git"
    echo ""
    echo "  Для SSH:"
    echo "    git remote set-url origin git@github.com:Alpine1428/Zalupa2.0.git"
    echo ""
    echo "  Потом запусти скрипт снова."
    echo "========================================="
    exit 1
fi