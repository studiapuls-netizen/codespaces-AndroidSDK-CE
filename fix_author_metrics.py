import os
import re
import shutil
import sys

FORMATTERS = "app/src/main/java/ru/asmr1/app/utils/Formatters.kt"
AUTHOR_PAGE = "app/src/main/java/ru/asmr1/app/author/AuthorPageActivity.kt"

NEW_FUNC = '''
fun formatViewsShort(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
    else -> n.toString()
}
'''

def ensure_locale_import(content):
    if "import java.util.Locale" in content:
        return content
    m = re.search(r"^(import .+)$", content, re.MULTILINE)
    if m:
        return content[:m.end()] + "\nimport java.util.Locale" + content[m.end():]
    m = re.search(r"^package .+$", content, re.MULTILINE)
    if m:
        return content[:m.end()] + "\n\nimport java.util.Locale\n" + content[m.end():]
    return content

def patch_formatters(path):
    if not os.path.isfile(path):
        print("НЕ НАЙДЕН: " + path)
        return False
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    if "fun formatViewsShort(" in content:
        print("Уже есть formatViewsShort в " + path)
        return True
    shutil.copy2(path, path + ".bak")
    content = ensure_locale_import(content)
    content = content.rstrip() + "\n\n" + NEW_FUNC.strip() + "\n"
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("OK: " + path + " (бэкап: " + path + ".bak)")
    return True

def patch_author_page(path):
    if not os.path.isfile(path):
        print("НЕ НАЙДЕН: " + path)
        return False
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    original = content
    content = re.sub(
        r"binding\.tvSubscribers\.text\s*=\s*formatViews\(([^)]+)\)",
        r"binding.tvSubscribers.text = formatViewsShort(\1)",
        content,
    )
    content = re.sub(
        r"binding\.tvViews\.text\s*=\s*formatViews\(([^)]+)\)",
        r"binding.tvViews.text = formatViewsShort(\1)",
        content,
    )
    if content == original:
        print("Нечего менять в " + path)
        return True
    shutil.copy2(path, path + ".bak")
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("OK: " + path + " (бэкап: " + path + ".bak)")
    return True

def main():
    print("Патчим Android-проект ASMR1...")
    ok1 = patch_formatters(FORMATTERS)
    ok2 = patch_author_page(AUTHOR_PAGE)
    if ok1 and ok2:
        print("ГОТОВО. Дальше: Build -> Make Project в Android Studio.")
        return 0
    print("Что-то пошло не так, смотри сообщения выше.")
    return 1

if __name__ == "__main__":
    sys.exit(main())
