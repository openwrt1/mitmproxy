from mitmproxy import ctx
from mitmproxy import http

# 这个函数会在每次有 WebSocket 消息传递时自动触发
def websocket_message(flow: http.HTTPFlow):
    # 获取当前正在传输的最新一条消息
    message = flow.websocket.messages[-1]
    
    # 判断：如果是从客户端（你的App）发往服务器的消息
    if message.from_client:
        ctx.log.info(f"原消息拦截成功: {message.content}")
        
        # 强制篡改内容为 3333
        message.content = b"3333"
        
        ctx.log.info("已成功替换为: 3333 发往服务器！")
