#!/usr/bin/env bash
set -euo pipefail

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
MAIN_JAR=$(ls "$APPDIR/lib"/*.jar 2>/dev/null | head -n 1)
if [ -z "$MAIN_JAR" ]; then
    echo "ERROR: No jar found in $APPDIR/lib"
    exit 1
fi
echo "Main jar: $MAIN_JAR"

# ─── Шаг 4: Очистить старый .deb ─────────────────────────────────
DEST_DIR="build/dist"
mkdir -p "$DEST_DIR"
rm -f "$DEST_DIR"/*.deb

# ─── Шаг 5: jpackage ─────────────────────────────────────────────
echo "=== Building .deb with jpackage ==="
jpackage \
    --type deb \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --input "$APPDIR/lib" \
    --main-jar "$(basename "$MAIN_JAR")" \
    --main-class "$MAIN_CLASS" \
    --java-options "--module-path $APPDIR --add-modules javafx.controls,javafx.fxml,javafx.graphics" \
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
