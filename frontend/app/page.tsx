"use client";

import React, { useState, useEffect, useCallback } from 'react';
import ReactFlow, { Background, Controls, Edge, Node, applyNodeChanges, applyEdgeChanges, NodeChange, EdgeChange } from 'reactflow';
import 'reactflow/dist/style.css';

// 1. Initial Map of our 3 Nodes
const initialNodes: Node[] = [
  { id: 'Coordinator', position: { x: 400, y: 100 }, data: { label: 'Coordinator' }, style: { background: '#1e293b', color: 'white', border: '2px solid #475569', borderRadius: '8px', padding: '15px', fontWeight: 'bold' } },
  { id: 'Node_A', position: { x: 200, y: 300 }, data: { label: 'Bank Node A' }, style: { background: '#1e293b', color: 'white', border: '2px solid #3b82f6', borderRadius: '8px', padding: '15px' } },
  { id: 'Node_B', position: { x: 600, y: 300 }, data: { label: 'Bank Node B' }, style: { background: '#1e293b', color: 'white', border: '2px solid #3b82f6', borderRadius: '8px', padding: '15px' } },
];

// 2. Initial Map of the Network Cables
const initialEdges: Edge[] = [
  { id: 'Coordinator-Node_A', source: 'Coordinator', target: 'Node_A', type: 'straight', style: { stroke: '#475569', strokeWidth: 2 } },
  { id: 'Coordinator-Node_B', source: 'Coordinator', target: 'Node_B', type: 'straight', style: { stroke: '#475569', strokeWidth: 2 } },
  { id: 'Node_A-Coordinator', source: 'Node_A', target: 'Coordinator', type: 'straight', style: { stroke: '#475569', strokeWidth: 2 } },
  { id: 'Node_B-Coordinator', source: 'Node_B', target: 'Coordinator', type: 'straight', style: { stroke: '#475569', strokeWidth: 2 } },
];

export default function Dashboard() {
  const [nodes, setNodes] = useState<Node[]>(initialNodes);
  const [edges, setEdges] = useState<Edge[]>(initialEdges);
  const [logs, setLogs] = useState<string[]>([]);
  const [isConnected, setIsConnected] = useState(false);

  const onNodesChange = useCallback((changes: NodeChange[]) => setNodes((nds) => applyNodeChanges(changes, nds)), []);
  const onEdgesChange = useCallback((changes: EdgeChange[]) => setEdges((eds) => applyEdgeChanges(changes, eds)), []);

  // 3. The WebSocket Connection
  useEffect(() => {
    const ws = new WebSocket('ws://localhost:7070/ws');

    ws.onopen = () => setIsConnected(true);
    ws.onclose = () => setIsConnected(false);

    ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      const logMessage = `[${data.time}ms] ${data.type}: ${data.from} -> ${data.to}`;

      // Add log to terminal window
      setLogs((prev) => [...prev, logMessage]);

      // Find the edge that represents this network connection
      const edgeId = `${data.from}-${data.to}`;

      if (data.type === 'PACKET_SENT') {
        // Flash Green and Animate
        setEdges((eds) => eds.map(e => e.id === edgeId ? { ...e, animated: true, style: { stroke: '#22c55e', strokeWidth: 4 } } : e));
      } else if (data.type === 'PACKET_DROP') {
        // Flash Red and Stop Animation
        setEdges((eds) => eds.map(e => e.id === edgeId ? { ...e, animated: false, style: { stroke: '#ef4444', strokeWidth: 4 } } : e));
      }

      // Revert edge color back to normal after 600ms
      setTimeout(() => {
        setEdges((eds) => eds.map(e => e.id === edgeId ? { ...e, animated: false, style: { stroke: '#475569', strokeWidth: 2 } } : e));
      }, 600);
    };

    return () => ws.close();
  }, []);

  // 4. The HTML Layout
  return (
      <div className="flex h-screen bg-slate-950 text-white font-sans">

        {/* LEFT SIDE: React Flow Graph */}
        <div className="w-2/3 h-full border-r border-slate-800 relative">
          <div className="absolute top-4 left-4 z-10 bg-slate-900 p-4 rounded-lg border border-slate-700 shadow-xl">
            <h1 className="text-xl font-bold mb-2">Deterministic Sandbox</h1>
            <div className="flex items-center gap-2">
              <div className={`w-3 h-3 rounded-full ${isConnected ? 'bg-green-500' : 'bg-red-500'}`}></div>
              <span className="text-sm text-slate-400">{isConnected ? 'Connected to Simulation' : 'Disconnected'}</span>
            </div>
          </div>

          <ReactFlow nodes={nodes} edges={edges} onNodesChange={onNodesChange} onEdgesChange={onEdgesChange} fitView>
            <Background color="#334155" gap={20} />
            <Controls className="bg-slate-800 fill-white" />
          </ReactFlow>
        </div>

        {/* RIGHT SIDE: Terminal Logs */}
        <div className="w-1/3 h-full flex flex-col bg-slate-900">
          <div className="p-4 border-b border-slate-800 bg-slate-950">
            <h2 className="font-semibold text-slate-200">Simulation Telemetry</h2>
          </div>
          <div className="flex-1 p-4 overflow-y-auto font-mono text-xs text-slate-300 space-y-2">
            {logs.map((log, i) => (
                <div key={i} className={`${log.includes('DROP') ? 'text-red-400' : 'text-green-400'}`}>
                  {log}
                </div>
            ))}
            {/* Invisible element to force scroll to bottom */}
            <div style={{ float:"left", clear: "both" }} />
          </div>
        </div>

      </div>
  );
}