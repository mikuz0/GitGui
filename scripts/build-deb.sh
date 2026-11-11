#!/usr/bin/env bash
set -euo pipefail

# ─── JDK 21 явно ─────────────────────────────────────────────────
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"

# ─── Конфигурация ─────────────────────────────────────────────────
APP_NAME="git-gui"
APP_VERSION="1.0.0"
MAIN_CLASS="com.gitgui.ui.App"
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# Переход в корень проекта
cd "$APP_DIR"

echo "=== Building application with Gradle ==="
./gradlew clean build installDist --no-daemon

# ─── Шаг 2: Найти установленную директорию ────────────────────────
APPDIR="build/install/git-gui"
if [ ! -d "$APPDIR" ]; then
    echo "ERROR: $APPDIR not found. installDist failed?"
    exit 1
fi
echo "Application directory: $APPDIR"

# ─── Шаг 3: Найти main jar ───────────────────────────────────────
# Gradle application plugin называет jar по rootProject.name (без версии),
# т.к. version в build.gradle.kts не задан. Поэтому — git-gui.jar.
MAIN_JAR="$APPDIR/lib/git-gui.jar"
if [ ! -f "$MAIN_JAR" ]; then
    echo "ERROR: Main jar not found: $MAIN_JAR"
    echo "Available jars:"
    ls -la "$APPDIR/lib/"
    exit 1
fi
echo "Main jar: $MAIN_JAR"

# ─── Шаг 4: Очистить старый .deb ─────────────────────────────────
DEST_DIR="build/dist"
mkdir -p "$DEST_DIR"
rm -f "$DEST_DIR"/*.deb

# ─── Шаг 5: jpackage ─────────────────────────────────────────────
# --runtime-image "$JAVA_HOME" — заставляет jpackage использовать готовый
# runtime-образ вместо вызова jlink (который падает на Ubuntu JDK 21 с JEP 493).
# \$APPDIR в --java-options — runtime-переменная launcher'а jpackage,
# а не путь сборки; /lib — директория с JavaFX-модулями внутри пакета.
echo "=== Building .deb with jpackage ==="
jpackage \
    --type deb \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --input "$APPDIR/lib" \
    --main-jar "$(basename "$MAIN_JAR")" \
    --main-class "$MAIN_CLASS" \
    --linux-shortcut \
    --linux-menu-group "Development" \
    --dest "$DEST_DIR"

# ─── Шаг 6: Финальный отчёт ──────────────────────────────────────
echo ""
echo "=== DONE ==="
ls -la "$DEST_DIR"/*.deb
echo ""
echo "To install:"
echo "  sudo dpkg -i $DEST_DIR/$(basename "$DEST_DIR"/*.deb)"
