#!/bin/bash

# ==========================================
# VPS Deployment Configuration
# ==========================================
# Please fill in your VPS details below:

VPS_USER="root"               # e.g., root or ubuntu
VPS_IP="103.11.77.126"              # Your VPS IP address
VPS_DIR="/opt/testserver"     # The directory on the VPS where the server will live
SSH_KEY="/Users/rocket/Documents/key/id_ed25519"       # Path to your private SSH key (if different, update it)
VPS_PORT="9922"               # The SSH port for your VPS

# ==========================================

# Switch to the directory where this script is located
cd "$(dirname "$0")"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Cleanup function
cleanup() {
    echo -e "\n${BLUE}Script closed. Stopping and removing remote PM2 services...${NC}"
    ssh -p $VPS_PORT -i $SSH_KEY $VPS_USER@$VPS_IP "pm2 delete testserver_http || true; pm2 delete testserver_https || true; pm2 delete testserver_ws || true; pm2 save"
    echo -e "${GREEN}Cleanup complete. Exiting.${NC}"
    exit 0
}

# Catch Ctrl+C (SIGINT) and termination signals
trap cleanup SIGINT SIGTERM

echo -e "${BLUE}Setting up remote directory...${NC}"
ssh -p $VPS_PORT -i $SSH_KEY $VPS_USER@$VPS_IP "mkdir -p $VPS_DIR"

echo -e "${BLUE}Performing initial sync...${NC}"
scp -P $VPS_PORT -i $SSH_KEY ./server.py $VPS_USER@$VPS_IP:$VPS_DIR/

echo -e "${GREEN}Initial sync complete!${NC}"
echo -e "${BLUE}Starting or Restarting remote PM2 services (HTTP, HTTPS & WS)...${NC}"
ssh -p $VPS_PORT -i $SSH_KEY $VPS_USER@$VPS_IP "cd $VPS_DIR && \
    (python3 -m pip install websockets || true) && \
    (pm2 restart testserver_http || pm2 start server.py --name 'testserver_http' --interpreter python3 -- --port 8080) && \
    (pm2 restart testserver_https || pm2 start server.py --name 'testserver_https' --interpreter python3 -- --port 2096 --https) && \
    (pm2 restart testserver_ws || pm2 start server.py --name 'testserver_ws' --interpreter python3 -- --port 2053 --ws) && \
    pm2 save"

echo -e "${GREEN}Watching for changes in server.py... (Press Ctrl+C to stop)${NC}"

# Simple loop to watch for changes based on file modification time
LAST_MOD=$(stat -f "%m" server.py 2>/dev/null || stat -c "%Y" server.py)


while true; do
    CURRENT_MOD=$(stat -f "%m" server.py 2>/dev/null || stat -c "%Y" server.py)
    
    if [ "$CURRENT_MOD" != "$LAST_MOD" ]; then
        echo -e "${BLUE}Change detected! Syncing...${NC}"
        scp -P $VPS_PORT -i $SSH_KEY ./server.py $VPS_USER@$VPS_IP:$VPS_DIR/
        ssh -p $VPS_PORT -i $SSH_KEY $VPS_USER@$VPS_IP "cd $VPS_DIR && \
            (pm2 restart testserver_http || pm2 start server.py --name 'testserver_http' --interpreter python3 -- --port 8080) && \
            (pm2 restart testserver_https || pm2 start server.py --name 'testserver_https' --interpreter python3 -- --port 2096 --https) && \
            (pm2 restart testserver_ws || pm2 start server.py --name 'testserver_ws' --interpreter python3 -- --port 2053 --ws) && \
            pm2 save"
        echo -e "${GREEN}Sync complete and servers restarted.${NC}"
        LAST_MOD=$CURRENT_MOD
    fi
    
    sleep 2
done
