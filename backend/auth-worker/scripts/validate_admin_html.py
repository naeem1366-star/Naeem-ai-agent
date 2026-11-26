from html.parser import HTMLParser
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "public" / "admin.html").read_text(encoding="utf-8")

class ScriptExtractor(HTMLParser):
    def __init__(self):
        super().__init__()
        self.in_script = False
        self.parts = []
    def handle_starttag(self, tag, attrs):
        if tag.lower() == "script":
            self.in_script = True
    def handle_endtag(self, tag):
        if tag.lower() == "script":
            self.in_script = False
    def handle_data(self, data):
        if self.in_script:
            self.parts.append(data)

parser = ScriptExtractor()
parser.feed(source)
javascript = "\n".join(parser.parts)
assert javascript.strip(), "admin.html has no inline JavaScript"
for required_id in ("loginBtn", "payments", "saveSettings", "enableBilling", "disableBilling", "addPricing"):
    assert f'id="{required_id}"' in source, f"Missing admin control: {required_id}"

with tempfile.NamedTemporaryFile("w", suffix=".js", encoding="utf-8", delete=False) as handle:
    handle.write(javascript)
    temp_path = handle.name
try:
    subprocess.run(["node", "--check", temp_path], check=True)
finally:
    Path(temp_path).unlink(missing_ok=True)

print("EMA admin HTML and JavaScript syntax OK")
