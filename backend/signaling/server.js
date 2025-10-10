import { WebSocketServer } from 'ws';

const wss = new WebSocketServer({ port: 8080, path: '/ws' });

// roomId -> Set of clients
const rooms = new Map();

function joinRoom(roomId, ws) {
  if (!rooms.has(roomId)) rooms.set(roomId, new Set());
  rooms.get(roomId).add(ws);
  ws.roomId = roomId;
}

function leaveRoom(ws) {
  const roomId = ws.roomId;
  if (!roomId) return;
  const set = rooms.get(roomId);
  if (set) {
    set.delete(ws);
    if (set.size === 0) rooms.delete(roomId);
  }
}

function broadcastToRoom(roomId, sender, data) {
  const set = rooms.get(roomId);
  if (!set) return;
  for (const client of set) {
    if (client !== sender && client.readyState === client.OPEN) {
      client.send(data);
    }
  }
}

wss.on('connection', (ws) => {
  ws.on('message', (raw) => {
    try {
      const msg = JSON.parse(raw.toString());
      const type = msg.type;
      const room = msg.room;
      if (type === 'join') {
        if (typeof room !== 'string') return;
        joinRoom(room, ws);
        return;
      }
      if (!room) return;
      // Relay offers/answers/ice to peers in same room
      if (type === 'offer' || type === 'answer' || type === 'ice') {
        broadcastToRoom(room, ws, JSON.stringify(msg));
      }
    } catch (e) {
      console.error('bad message', e);
    }
  });
  ws.on('close', () => leaveRoom(ws));
});

console.log('Signaling server listening on ws://0.0.0.0:8080/ws');
