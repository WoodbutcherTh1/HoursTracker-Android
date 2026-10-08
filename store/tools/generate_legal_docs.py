#!/usr/bin/env python3
"""Writes store/legal/*.md from the in-app legal strings, so the policy published for Google Play is word for word the one in the app.

Run from the repository root:  python3 store/tools/generate_legal_docs.py
"""
import html
import os
import re

RES = "app/src/main/res"
TERMS = ["service", "estimates", "responsibility", "account", "use", "liability", "changes", "law", "contact"]
PRIVACY = ["data", "account", "support", "tracking", "retention", "rights", "security", "controls", "children", "changes", "contact"]
FOLDERS = {"values": "en", "values-iw": "he", "values-ar": "ar", "values-ru": "ru"}


def load(folder):
    text = open(f"{RES}/{folder}/strings_legaltext.xml", encoding="utf-8").read()
    strings = {}
    for key, value in re.findall(r'<string name="([^"]+)">(.*?)</string>', text, re.S):
        strings[key] = html.unescape(value).replace("\\'", "'").replace("\\@", "@").replace('\\"', '"')
    return strings


os.makedirs("store/legal", exist_ok=True)
for folder, lang in FOLDERS.items():
    t = load(folder)
    for kind, order in (("privacy", PRIVACY), ("terms", TERMS)):
        md = f"# {t[kind + '_title']}\n\n"
        if kind == "privacy":
            md += f"_{t['privacy_updated']}_\n\n"
        md += t[kind + "_intro"] + "\n\n"
        for key in order:
            md += f"## {t[f'{kind}_section_{key}']}\n\n{t[f'{kind}_body_{key}']}\n\n"
        open(f"store/legal/{kind.upper()}_{lang}.md", "w", encoding="utf-8").write(md)
print("wrote", len(FOLDERS) * 2, "documents")
