import os
import requests
import websocket

TOKEN = os.getenv("TOKEN", "PLEASE_SET_TOKEN")
BASE_HTTP = os.getenv("BASE_HTTP", "http://localhost:8080")
WS_URL = os.getenv("WS_URL", f"ws://localhost:8080/ws/chat?token={TOKEN}")


def test_http():
    headers = {"Authorization": TOKEN}

    print("\n=== HTTP: /im/sessions ===")
    r = requests.get(f"{BASE_HTTP}/im/sessions", headers=headers, timeout=8)
    print("status=", r.status_code)
    print(r.text)

    print("\n=== HTTP: /im/search/user?keyword=admin ===")
    r = requests.get(
        f"{BASE_HTTP}/im/search/user",
        headers=headers,
        params={"keyword": "admin"},
        timeout=8,
    )
    print("status=", r.status_code)
    print(r.text)


def test_ws():
    print("\n=== WS CONNECT ===")
    print("WS_URL=", WS_URL)

    def on_open(_ws):
        print("[ws] open")

    def on_message(_ws, message):
        print("[ws] message:", message)

    def on_error(_ws, error):
        print("[ws] error:", error)

    def on_close(_ws, close_status_code, close_msg):
        print(f"[ws] close: code={close_status_code}, reason={close_msg}")

    ws = websocket.WebSocketApp(
        WS_URL,
        header=[f"Authorization: {TOKEN}"],
        on_open=on_open,
        on_message=on_message,
        on_error=on_error,
        on_close=on_close,
    )

    ws.run_forever(ping_interval=10, ping_timeout=5)


if __name__ == "__main__":
    if not TOKEN or TOKEN == "PLEASE_SET_TOKEN":
        raise SystemExit(
            "Missing TOKEN. Use:\n"
            "  PowerShell: $env:TOKEN=\"xxx\"; python .\\im_test.py\n"
            "  or set TOKEN in your environment."
        )

    test_http()
    test_ws()
