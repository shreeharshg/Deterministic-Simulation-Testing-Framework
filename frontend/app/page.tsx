"use client";

import React, { useState, useEffect, useCallback, useRef } from 'react';
import ReactFlow, { Background, Controls, Edge, Node, applyNodeChanges, applyEdgeChanges, Handle, Position, NodeChange, EdgeChange } from 'reactflow';
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import { Activity, Database, Server, ShieldAlert, Cpu, Play, Zap, Settings2 } from 'lucide-react';
import 'reactflow/dist/style.css';

// ============================================================================
// 1. CUSTOM REACT FLOW NODES
// ============================================================================
const CustomServerNode = ({ data }: { data: any }) => {
  return (
      <div className="bg-[#0D1117] border border-[#30363D] rounded-lg shadow-2xl w-64 overflow-hidden font-mono text-xs">
        <div className="bg-[#161B22] p-2 border-b border-[#30363D] flex justify-between items-center">
          <div className="flex items-center gap-2 text-[#C9D1D9]">
            {data.type === 'coordinator' ? <Cpu size={14} className="text-[#A371F7]" /> : <Server size={14} className="text-[#58A6FF]" />}
            <span className="font-bold tracking-wider uppercase">{data.label}</span>
          </div>
          <div className="w-2 h-2 rounded-full bg-[#3FB950] animate-pulse shadow-[0_0_8px_#3FB950]"></div>
        </div>
        <div className="p-3 space-y-2">
          {data.balance !== undefined && (
              <>
                <div className="flex justify-between items-center bg-[#090C10] p-1.5 rounded border border-[#21262D]">
                  <span className="text-[#8B949E]">AVAILABLE BAL</span>
                  <span className="text-[#3FB950] font-bold">${data.balance}</span>
                </div>
                <div className="flex justify-between items-center bg-[#090C10] p-1.5 rounded border border-[#21262D]">
                  <span className="text-[#8B949E]">LOCKED IN 2PC</span>
                  <span className={`${data.locked > 0 ? 'text-[#F85149] animate-pulse' : 'text-[#8B949E]'} font-bold`}>
                ${data.locked || 0}
              </span>
                </div>
              </>
          )}
        </div>
        <Handle type="target" position={Position.Top} className="w-3 h-3 bg-[#58A6FF] border-none" />
        <Handle type="source" position={Position.Bottom} className="w-3 h-3 bg-[#58A6FF] border-none" />
      </div>
  );
};

const nodeTypes = { customServer: CustomServerNode };

// ============================================================================
// 2. MAIN DASHBOARD COMPONENT
// ============================================================================
export default function EnterpriseDashboard() {
  const [nodes, setNodes] = useState<Node[]>([
    { id: 'Coordinator', type: 'customServer', position: { x: 400, y: 50 }, data: { label: '2PC Coordinator', type: 'coordinator' } },
    { id: 'Node_A', type: 'customServer', position: { x: 150, y: 350 }, data: { label: 'Bank Node A', balance: 500, locked: 0, type: 'bank' } },
    { id: 'Node_B', type: 'customServer', position: { x: 650, y: 350 }, data: { label: 'Bank Node B', balance: 100, locked: 0, type: 'bank' } },
  ]);

  const [edges, setEdges] = useState<Edge[]>([
    { id: 'Coordinator-Node_A', source: 'Coordinator', target: 'Node_A', type: 'smoothstep', style: { stroke: '#30363D', strokeWidth: 2 } },
    { id: 'Coordinator-Node_B', source: 'Coordinator', target: 'Node_B', type: 'smoothstep', style: { stroke: '#30363D', strokeWidth: 2 } },
    { id: 'Node_A-Coordinator', source: 'Node_A', target: 'Coordinator', type: 'smoothstep', style: { stroke: '#30363D', strokeWidth: 2 } },
    { id: 'Node_B-Coordinator', source: 'Node_B', target: 'Coordinator', type: 'smoothstep', style: { stroke: '#30363D', strokeWidth: 2 } },
  ]);

  const [storyLogs, setStoryLogs] = useState<any[]>([]);
  const [txLogs, setTxLogs] = useState<any[]>([]);
  const [fuzzerLogs, setFuzzerLogs] = useState<any[]>([]);
  const [chartData, setChartData] = useState<any[]>([{ time: 0, sent: 0, dropped: 0 }]);

  const [isConnected, setIsConnected] = useState(false);
  const [isFuzzing, setIsFuzzing] = useState<boolean>(false);

  const wsClient = useRef<WebSocket | any>(null);

  // DYNAMIC SETTINGS
  const [seedInput, setSeedInput] = useState<string>("42");
  const [dropRate, setDropRate] = useState<number>(0.20);
  const [minLat, setMinLat] = useState<number>(10);
  const [maxLat, setMaxLat] = useState<number>(100);
  const [fuzzerUniverses, setFuzzerUniverses] = useState<number>(10000);
  const [fuzzerTx, setFuzzerTx] = useState<number>(10);

  const onNodesChange = useCallback((changes: NodeChange[]) => setNodes((nds) => applyNodeChanges(changes, nds)), []);
  const onEdgesChange = useCallback((changes: EdgeChange[]) => setEdges((eds) => applyEdgeChanges(changes, eds)), []);

  // ============================================================================
  // 3. WEBSOCKET ENGINE
  // ============================================================================
  useEffect(() => {
    const ws = new WebSocket('ws://localhost:7070/ws');
    wsClient.current = ws;

    ws.onopen = () => setIsConnected(true);
    ws.onclose = () => setIsConnected(false);

    ws.onmessage = (event) => {
      const data = JSON.parse(event.data);

      if (data.type === 'METRICS') {
        setNodes((nds) => nds.map((n): Node => {
          if (n.id === 'Node_A') return { ...n, data: { ...n.data, balance: data.balanceA, locked: data.lockedA } };
          if (n.id === 'Node_B') return { ...n, data: { ...n.data, balance: data.balanceB, locked: data.lockedB } };
          return n;
        }));
        setChartData(prev => [...prev.slice(-20), { time: data.time, sent: Math.random() * 100 + 50, dropped: Math.random() * 20 }]);
      }

      if (data.type === 'TX_SUCCESS' || data.type === 'TX_FAILED') {
        setTxLogs(prev => [{ id: data.txId, status: data.type, time: data.time || new Date().toISOString().split('T')[1].slice(0,-1) }, ...prev].slice(0, 50));
      }

      if (data.type === 'PACKET_SENT' || data.type === 'PACKET_DROP') {
        const isDrop = data.type === 'PACKET_DROP';
        const edgeId = `${data.from}-${data.to}`;

        const storyMsg = isDrop
            ? `💥 CHAOS: Packet destroyed in transit from ${data.from} to ${data.to}.`
            : `✉️ ${data.from} successfully transmitted data to ${data.to}.`;

        setStoryLogs(prev => [{ msg: storyMsg, isDrop, time: data.time }, ...prev].slice(0, 20));

        setEdges((eds) => eds.map((e): Edge =>
            e.id === edgeId ? { ...e, animated: !isDrop, style: { stroke: isDrop ? '#F85149' : '#3FB950', strokeWidth: 3, filter: `drop-shadow(0 0 5px ${isDrop ? '#F85149' : '#3FB950'})` } } : e
        ));

        setTimeout(() => {
          setEdges((eds) => eds.map((e): Edge => e.id === edgeId ? { ...e, animated: false, style: { stroke: '#30363D', strokeWidth: 2, filter: 'none' } } : e));
        }, 200);
      }

      if (data.type === 'FUZZER_LOG') {
        const isError = data.status === 'ERROR';
        setFuzzerLogs(prev => [{ msg: data.msg, isError, time: new Date().toLocaleTimeString() }, ...prev].slice(0, 100));
      }

      if (data.type === 'FUZZER_STOPPED') {
        setIsFuzzing(false);
      }
    };
    return () => ws.close();
  }, []);

  // ============================================================================
  // 4. CONTROL HANDLERS
  // ============================================================================
  const handleRunVisual = () => {
    setTxLogs([]); setStoryLogs([]);
    setNodes((nds) => nds.map((n): Node => {
      if (n.id === 'Node_A') return { ...n, data: { ...n.data, balance: 500, locked: 0 } };
      if (n.id === 'Node_B') return { ...n, data: { ...n.data, balance: 100, locked: 0 } };
      return n;
    }));
    wsClient.current?.send(JSON.stringify({ action: 'RUN_SINGLE', seed: parseInt(seedInput), dropRate, minLat, maxLat }));
  };

  const handleRunFuzzer = () => {
    setFuzzerLogs([]); setIsFuzzing(true);
    wsClient.current?.send(JSON.stringify({ action: 'RUN_FUZZER', universes: fuzzerUniverses, tx: fuzzerTx, dropRate, minLat, maxLat }));
  };

  const handleStopFuzzer = () => wsClient.current?.send(JSON.stringify({ action: 'STOP_FUZZER' }));

  // ============================================================================
  // 5. RENDER UI
  // ============================================================================
  return (
      <div className="flex flex-col h-screen bg-[#010409] text-[#C9D1D9] font-sans overflow-hidden">

        {/* TOP NAVBAR */}
        <div className="h-14 bg-[#161B22] border-b border-[#30363D] flex items-center justify-between px-6 shadow-md z-20">
          <div className="flex items-center gap-3">
            <Activity className="text-[#A371F7]" size={20} />
            <h1 className="text-lg font-bold tracking-widest text-[#E6EDF3]">FOUNDATION<span className="text-[#8B949E]">TEST</span></h1>
          </div>
          <div className="flex items-center gap-3 font-mono text-xs">
            {isConnected ?
                <span className="text-[#3FB950] bg-[#3FB950]/10 px-2 py-1 rounded border border-[#3FB950]/30 flex items-center gap-2"><div className="w-2 h-2 rounded-full bg-[#3FB950] animate-pulse"></div> SERVER CONNECTED</span> :
                <span className="text-[#F85149] bg-[#F85149]/10 px-2 py-1 rounded border border-[#F85149]/30 flex items-center gap-2"><ShieldAlert size={14}/> SERVER OFFLINE</span>
            }
          </div>
        </div>

        <div className="flex flex-1 overflow-hidden">

          {/* LEFT PANE: Graph & Chaos Settings */}
          <div className="w-3/5 h-full relative border-r border-[#30363D] bg-[#0D1117] shadow-inner flex flex-col">

            <div className="bg-[#161B22] border-b border-[#30363D] p-3 flex justify-between items-center z-10">
              <div className="flex items-center gap-4">
                <div className="flex flex-col">
                  <label className="text-[9px] font-bold text-[#8B949E] uppercase mb-0.5"><Settings2 size={10} className="inline mr-1"/>Chaos Drop %</label>
                  <input type="number" step="0.05" value={dropRate} onChange={(e)=>setDropRate(Number(e.target.value))} className="w-20 bg-[#010409] text-[#58A6FF] text-xs px-2 py-1 rounded border border-[#30363D] font-mono" />
                </div>
                <div className="flex flex-col">
                  <label className="text-[9px] font-bold text-[#8B949E] uppercase mb-0.5">Latency (ms)</label>
                  <div className="flex gap-1 items-center">
                    <input type="number" value={minLat} onChange={(e)=>setMinLat(Number(e.target.value))} className="w-14 bg-[#010409] text-[#58A6FF] text-xs px-2 py-1 rounded border border-[#30363D] font-mono" />
                    <span className="text-[#8B949E]">-</span>
                    <input type="number" value={maxLat} onChange={(e)=>setMaxLat(Number(e.target.value))} className="w-14 bg-[#010409] text-[#58A6FF] text-xs px-2 py-1 rounded border border-[#30363D] font-mono" />
                  </div>
                </div>
                <div className="w-px h-6 bg-[#30363D] mx-2"></div>
                <div className="flex flex-col">
                  <label className="text-[9px] font-bold text-[#8B949E] uppercase mb-0.5">PRNG Seed</label>
                  <input type="number" value={seedInput} onChange={(e)=>setSeedInput(e.target.value)} className="w-20 bg-[#010409] text-[#58A6FF] text-xs px-2 py-1 rounded border border-[#30363D] font-mono" />
                </div>
                <button onClick={handleRunVisual} className="mt-4 flex items-center gap-1 bg-[#238636] hover:bg-[#2EA043] text-white text-xs font-bold px-3 py-1.5 rounded shadow">
                  <Play size={14}/> PLAY VISUAL
                </button>
              </div>
            </div>

            <ReactFlow nodes={nodes} edges={edges} nodeTypes={nodeTypes} onNodesChange={onNodesChange} onEdgesChange={onEdgesChange} fitView className="bg-[#0D1117] flex-1">
              <Background color="#30363D" gap={30} size={1} />
              <Controls className="bg-[#161B22] border-[#30363D] fill-[#8B949E]" />
            </ReactFlow>

            <div className="absolute bottom-4 left-4 right-4 h-32 bg-[#010409]/90 border border-[#30363D] rounded-lg p-3 overflow-y-auto font-mono text-[11px] shadow-2xl backdrop-blur-sm z-10">
              <h3 className="text-[#8B949E] font-bold text-[10px] mb-2 uppercase">Story Mode Telemetry</h3>
              {storyLogs.map((log, i) => (
                  <div key={i} className={`mb-1 ${log.isDrop ? 'text-[#F85149]' : 'text-[#8B949E]'}`}>
                    <span className="text-[#484F58]">[{log.time}ms]</span> {log.msg}
                  </div>
              ))}
            </div>
          </div>

          <div className="w-2/5 h-full flex flex-col bg-[#010409]">

            <div className="h-[40%] border-b border-[#30363D] p-4 flex flex-col">
              <h2 className="text-xs font-bold text-[#8B949E] tracking-widest mb-3 flex items-center gap-2"><Database size={14}/> 2PC LEDGER STREAM</h2>
              <div className="grid grid-cols-3 text-[10px] font-bold text-[#8B949E] pb-2 border-b border-[#30363D] uppercase tracking-wider">
                <div>Transaction ID</div>
                <div>Status</div>
                <div className="text-right">Timestamp</div>
              </div>
              <div className="flex-1 overflow-hidden hover:overflow-y-auto mt-2 space-y-1 font-mono text-[11px] pr-2 custom-scrollbar">
                {txLogs.map((log, i) => (
                    <div key={i} className={`grid grid-cols-3 p-2 rounded items-center ${log.status === 'TX_SUCCESS' ? 'bg-[#3FB950]/10 border border-[#3FB950]/20' : 'bg-[#F85149]/10 border border-[#F85149]/20'}`}>
                      <div className="text-[#E6EDF3]">{log.id}</div>
                      <div>
                        {log.status === 'TX_SUCCESS' ? <span className="text-[#3FB950]">✓ COMMITTED</span> : <span className="text-[#F85149]">✗ ROLLED BACK</span>}
                      </div>
                      <div className="text-right text-[#8B949E]">{log.time}</div>
                    </div>
                ))}
              </div>
            </div>

            <div className="flex-1 flex flex-col p-4 bg-[#0D1117]">
              <div className="flex justify-between items-end mb-3 pb-3 border-b border-[#30363D]">
                <div className="flex gap-4">
                  <div className="flex flex-col gap-1">
                    <label className="text-[10px] font-bold text-[#8B949E] uppercase">Universes</label>
                    <input type="number" value={fuzzerUniverses} onChange={(e) => setFuzzerUniverses(Number(e.target.value))} className="w-24 bg-[#010409] text-[#58A6FF] text-xs px-2 py-1 rounded border border-[#30363D] focus:outline-none focus:border-[#58A6FF] font-mono" />
                  </div>
                  <div className="flex flex-col gap-1">
                    <label className="text-[10px] font-bold text-[#8B949E] uppercase">Tx / Universe</label>
                    <input type="number" value={fuzzerTx} onChange={(e) => setFuzzerTx(Number(e.target.value))} className="w-24 bg-[#010409] text-[#58A6FF] text-xs px-2 py-1 rounded border border-[#30363D] focus:outline-none focus:border-[#58A6FF] font-mono" />
                  </div>
                </div>
                {!isFuzzing ? (
                    <button onClick={handleRunFuzzer} className="flex items-center gap-1 bg-[#1F6FEB] hover:bg-[#388BFD] text-white text-xs font-bold px-4 py-2 rounded shadow border border-[#1F6FEB] h-8">
                      <Zap size={14}/> START MASS FUZZING
                    </button>
                ) : (
                    <button onClick={handleStopFuzzer} className="flex items-center gap-1 bg-[#F85149] hover:bg-[#FF7B72] text-white text-xs font-bold px-4 py-2 rounded shadow border border-[#F85149] h-8 animate-pulse">
                      🛑 ABORT FUZZER
                    </button>
                )}
              </div>
              <h2 className="text-xs font-bold text-[#8B949E] tracking-widest mb-2 flex items-center gap-2"><Zap size={14}/> MASS FUZZER CONSOLE</h2>
              <div className="flex-1 overflow-y-auto font-mono text-[11px] bg-[#010409] border border-[#30363D] rounded p-2">
                {fuzzerLogs.map((log, i) => (
                    <div key={i} className={`mb-1 ${log.isError ? 'text-[#F85149] font-bold' : 'text-[#8B949E]'}`}>
                      <span className="text-[#484F58]">[{log.time}]</span> {log.msg}
                    </div>
                ))}
              </div>
            </div>

          </div>
        </div>
      </div>
  );
}