import json
import logging
import argparse
import ssl
import os
import asyncio
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')

class TestServerHandler(BaseHTTPRequestHandler):
    def _set_headers(self, status_code=200, content_type='application/json'):
        self.send_response(status_code)
        self.send_header('Content-type', content_type)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()

    def do_GET(self):
        logging.info(f"GET request,\nPath: {self.path}\nHeaders:\n{self.headers}")
        
        parsed_path = urlparse(self.path)
        query_params = parse_qs(parsed_path.query)

        self._set_headers()
        response = {
            "status": "success",
            "method": "GET",
            "path": parsed_path.path,
            "query": query_params,
            "message": "Hello from mitmproxy test server (GET)"
        }
        self.wfile.write(json.dumps(response).encode('utf-8'))

    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        post_data = self.rfile.read(content_length) if content_length > 0 else b""
        
        logging.info(f"POST request,\nPath: {self.path}\nHeaders:\n{self.headers}\nBody:\n{post_data.decode('utf-8')}")
        
        self._set_headers()
        response = {
            "status": "success",
            "method": "POST",
            "path": self.path,
            "received_body": post_data.decode('utf-8'),
            "message": "Hello from mitmproxy test server (POST)"
        }
        self.wfile.write(json.dumps(response).encode('utf-8'))

def run(server_class=HTTPServer, handler_class=TestServerHandler, port=8080, use_https=False):
    server_address = ('0.0.0.0', port)
    httpd = server_class(server_address, handler_class)
    
    if use_https:
        cert_file = 'server.pem'
        if not os.path.exists(cert_file):
            # Generate a self-signed cert automatically using openssl
            os.system(f'openssl req -new -x509 -keyout {cert_file} -out {cert_file} -days 365 -nodes -subj "/CN=test.pengproxy.dpdns.org" 2>/dev/null')
        
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(certfile=cert_file)
        httpd.socket = context.wrap_socket(httpd.socket, server_side=True)
        logging.info(f"Starting test server on HTTPS port {port}...")
    else:
        logging.info(f"Starting test server on HTTP port {port}...")
        
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()
    logging.info("Stopping test server...")

def run_ws_server(port=8081):
    try:
        import websockets
    except ImportError:
        logging.error("websockets package not installed. Run: pip install websockets")
        return
        
    async def echo(websocket):
        logging.info(f"WebSocket client connected")
        try:
            async for message in websocket:
                logging.info(f"Received WS message: {message}")
                await websocket.send(f"Echo from server: {message}")
        except websockets.exceptions.ConnectionClosed:
            logging.info("WebSocket client disconnected")
            
    async def main():
        logging.info(f"Starting test WebSocket server on port {port}...")
        async with websockets.serve(echo, "0.0.0.0", port):
            await asyncio.Future()  # run forever

    asyncio.run(main())

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="Test Server")
    parser.add_argument('--port', type=int, default=8080, help='Port to listen on')
    parser.add_argument('--https', action='store_true', help='Enable HTTPS')
    parser.add_argument('--ws', action='store_true', help='Run as WebSocket server instead of HTTP')
    args = parser.parse_args()
    
    if args.ws:
        run_ws_server(port=args.port)
    else:
        run(port=args.port, use_https=args.https)
