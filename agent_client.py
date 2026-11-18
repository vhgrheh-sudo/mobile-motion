#!/usr/bin/env python3
"""Agent client for AI Mobile Motions. Talks to the app over TCP 4091.
Wire via USB: adb forward tcp:4091 tcp:4091
Wireless: Tailscale IP of the phone, port 4091.
"""
import argparse, json, socket

def send(host, port, req):
    s = socket.create_connection((host, port), timeout=120)
    s.sendall((json.dumps(req) + "\n").encode())
    data = b""
    while not data.endswith(b"\n"):
        chunk = s.recv(8192)
        if not chunk: break
        data += chunk
    s.close()
    return json.loads(data.decode())

def main():
    ap = argparse.ArgumentParser(prog="amm-agent")
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=4091)
    ap.add_argument("--agent", default="hermes")
    ap.add_argument("cmd", choices=["ping", "info", "selftest", "render", "preview"])
    ap.add_argument("--project-json")
    ap.add_argument("--frame", type=int, default=0)
    a = ap.parse_args()
    req = {"cmd": a.cmd, "agent": a.agent}
    if a.cmd == "render":
        req["project"] = json.load(open(a.project_json)) if a.project_json else {}
    if a.cmd == "preview":
        req["frame"] = a.frame
        if a.project_json: req["project"] = json.load(open(a.project_json))
    print(json.dumps(send(a.host, a.port, req), indent=2))

if __name__ == "__main__":
    main()
