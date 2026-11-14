import React, { useState, useEffect, useRef } from 'react';
import './App.css';

const API_BASE = window.location.hostname === 'localhost' ? 'http://localhost:3001' : '';

function CustomSelect({ label, options, value, onChange, disabled }) {
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState('');
  const containerRef = useRef(null);

  useEffect(() => {
    function handleClickOutside(event) {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, []);

  const filteredOptions = options.filter(opt => 
    opt.toLowerCase().includes(search.toLowerCase())
  );

  const handleSelect = (opt) => {
    onChange(opt);
    setIsOpen(false);
    setSearch('');
  };

  const handleToggle = () => {
    if (!disabled) {
      setIsOpen(!isOpen);
    }
  };

  return (
    <div className="input-group">
      <label>{label}</label>
      <div className={`custom-select-container ${isOpen ? 'open' : ''}`} ref={containerRef}>
        <button
          type="button"
          className="custom-select-trigger"
          onClick={handleToggle}
          disabled={disabled}
        >
          <span className="custom-select-value" title={value || 'Select option...'}>
            {value || 'Select option...'}
          </span>
          <span className="custom-select-arrow"></span>
        </button>

        {isOpen && (
          <div className="custom-select-dropdown">
            {options.length > 5 && (
              <div className="custom-select-search">
                <input
                  type="text"
                  placeholder="Search..."
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  autoFocus
                />
              </div>
            )}
            <div className="custom-select-options">
              {filteredOptions.length === 0 ? (
                <div className="custom-select-no-results">No results found</div>
              ) : (
                filteredOptions.map(opt => (
                  <div
                    key={opt}
                    className={`custom-select-option ${opt === value ? 'selected' : ''}`}
                    onClick={() => handleSelect(opt)}
                    title={opt}
                  >
                    {opt}
                  </div>
                ))
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function SipLadderDiagram({ data, onSelectMessage, selectedMsg }) {
  const hosts = Array.from(new Set(data.flatMap(m => [m.source, m.target])));
  if (hosts.length === 0) {
    return <div className="console-empty" style={{ margin: '100px auto' }}>No SIP messages found in log to render ladder.</div>;
  }
  
  const hostCols = hosts.sort((a,b) => {
    if (a.toLowerCase().includes('local') || a.includes('5060')) return -1;
    if (b.toLowerCase().includes('local') || b.includes('5060')) return 1;
    return 0;
  });

  const colWidth = 220;
  const startX = 100;
  const startY = 70;
  const rowHeight = 60;
  const svgWidth = startX * 2 + (hostCols.length - 1) * colWidth;
  const svgHeight = startY + data.length * rowHeight + 40;

  return (
    <div style={{ overflowX: 'auto', background: '#07090e', border: '1px solid var(--border-color)', borderRadius: '12px', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, overflowY: 'auto', padding: '10px' }}>
        <svg width={Math.max(svgWidth, 500)} height={svgHeight} style={{ fontFamily: 'Inter, sans-serif' }}>
          <defs>
            <marker id="arrow-out" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--accent-cyan)" />
            </marker>
            <marker id="arrow-in" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--accent-green)" />
            </marker>
            <marker id="arrow-selected" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#ff00ff" />
            </marker>
          </defs>

          {/* Dotted lines and headers */}
          {hostCols.map((host, idx) => {
            const cx = startX + idx * colWidth;
            return (
              <g key={host}>
                <line
                  x1={cx}
                  y1={startY - 20}
                  x2={cx}
                  y2={svgHeight - 20}
                  stroke="rgba(255,255,255,0.15)"
                  strokeWidth="2"
                  strokeDasharray="4,4"
                />
                <rect
                  x={cx - 90}
                  y={startY - 45}
                  width="180"
                  height="30"
                  rx="6"
                  fill="#101622"
                  stroke="var(--border-color)"
                  strokeWidth="1"
                />
                <text
                  x={cx}
                  y={startY - 25}
                  textAnchor="middle"
                  fill="#fff"
                  fontSize="12"
                  fontWeight="600"
                >
                  {host}
                </text>
              </g>
            );
          })}

          {/* Message arrows */}
          {data.map((msg, idx) => {
            const fromIdx = hostCols.indexOf(msg.source);
            const toIdx = hostCols.indexOf(msg.target);
            const fromX = fromIdx !== -1 ? startX + fromIdx * colWidth : startX;
            const toX = toIdx !== -1 ? startX + toIdx * colWidth : startX + colWidth;
            const y = startY + idx * rowHeight + 20;

            const isSelected = selectedMsg && selectedMsg.rawText === msg.rawText;
            const arrowColor = isSelected ? '#ff00ff' : (msg.direction === 'out' ? 'var(--accent-cyan)' : 'var(--accent-green)');
            const markerId = isSelected ? 'url(#arrow-selected)' : (msg.direction === 'out' ? 'url(#arrow-out)' : 'url(#arrow-in)');

            return (
              <g
                key={idx}
                cursor="pointer"
                onClick={() => onSelectMessage(msg)}
              >
                <line
                  x1={fromX}
                  y1={y}
                  x2={toX}
                  y2={y}
                  stroke="transparent"
                  strokeWidth="16"
                />
                <line
                  x1={fromX}
                  y1={y}
                  x2={toX}
                  y2={y}
                  stroke={arrowColor}
                  strokeWidth={isSelected ? '2.5' : '1.5'}
                  markerEnd={markerId}
                />
                <text
                  x={(fromX + toX) / 2}
                  y={y - 8}
                  textAnchor="middle"
                  fill={arrowColor}
                  fontSize="11"
                  fontWeight={isSelected ? '700' : '500'}
                >
                  {msg.label}
                </text>
                <circle
                  cx={fromX}
                  cy={y}
                  r="8"
                  fill={arrowColor}
                />
                <text
                  x={fromX}
                  y={y + 3}
                  textAnchor="middle"
                  fill="#000"
                  fontSize="9"
                  fontWeight="bold"
                >
                  {idx + 1}
                </text>
              </g>
            );
          })}
        </svg>
      </div>
    </div>
  );
}

function getResolvedPreview(scenarioText, valuesText, settings) {
  if (!scenarioText) return '';
  
  let resolved = scenarioText;
  
  const localIp = settings.serversInfo?.localIPAddress || '192.168.1.50';
  const localPort = settings.serversInfo?.localPort || '5060';
  const remoteIp = settings.serversInfo?.remoteIPAddress || '192.168.1.10';
  const remotePort = settings.serversInfo?.remotePort || '5060';
  const username = settings.account?.username || '70200';
  
  resolved = resolved.replace(/\[local_ip\]/g, localIp);
  resolved = resolved.replace(/\[local_port\]/g, localPort);
  resolved = resolved.replace(/\[remote_ip\]/g, remoteIp);
  resolved = resolved.replace(/\[remote_port\]/g, remotePort);
  resolved = resolved.replace(/\[call_id\]/g, 'c2901-abcde-12345-mock-call-id');
  resolved = resolved.replace(/\[username\]/g, username);
  
  if (valuesText) {
    const lines = valuesText.split(/\r?\n/).map(l => l.trim()).filter(Boolean);
    if (lines.length >= 2) {
      const headers = lines[0].split(',').map(h => h.trim());
      const values = lines[1].split(',').map(v => v.trim());
      
      headers.forEach((h, idx) => {
        let val = values[idx] || '';
        if (idx === 0) {
          if (!val || val === '1000' || val === '70200') {
            val = username;
          }
        }
        const regexField = new RegExp(`\\[field${idx}\\]`, 'g');
        resolved = resolved.replace(regexField, val);
        const regexHeader = new RegExp(`\\[${h}\\]`, 'g');
        resolved = resolved.replace(regexHeader, val);
      });
    }
  }
  
  return resolved;
}

function validateScenario(content) {
  const warnings = [];
  if (!content) return warnings;

  const blocks = content.split(/(?=u->|u<-|---------->|<----------)/);
  
  blocks.forEach((block, index) => {
    const trimmed = block.trim();
    if (!trimmed) return;
    
    const lines = trimmed.split(/\r?\n/).map(l => l.trim()).filter(Boolean);
    if (lines.length === 0) return;
    
    const onlyComments = lines.every(l => l.startsWith('//'));
    if (onlyComments) return;
    
    let firstLine = lines[0];
    if (firstLine.endsWith('*')) {
      firstLine = firstLine.slice(0, -1).trim();
    }
    if (firstLine !== 'u->' && firstLine !== 'u<-' && firstLine !== '---------->' && firstLine !== '<----------') {
      warnings.push(`Block ${index + 1}: Block does not start with a valid direction arrow (e.g. ----------> or u->). Found: "${firstLine.substring(0, 15)}"`);
      return;
    }
    
    const openBrackets = (trimmed.match(/\[/g) || []).length;
    const closeBrackets = (trimmed.match(/\]/g) || []).length;
    if (openBrackets !== closeBrackets) {
      warnings.push(`Block ${index + 1}: Unbalanced placeholder brackets. Found ${openBrackets} '[' and ${closeBrackets} ']'.`);
    }

    if (lines.length > 1) {
      const containsSip = lines.some(l => l.includes('SIP/2.0') || l.startsWith('INVITE') || l.startsWith('ACK') || l.startsWith('BYE') || l.startsWith('CANCEL') || l.startsWith('REFER') || l.startsWith('REGISTER'));
      if (containsSip) {
        if (!block.endsWith('\n\n') && !block.endsWith('\r\n\r\n') && !block.endsWith('\n\r\n')) {
          warnings.push(`Block ${index + 1}: Message block might be missing trailing double CRLF separator line.`);
        }
      }
    }
  });

  return warnings;
}

function RtpDiagnosticsTab({ API_BASE }) {
  const [rtpStats, setRtpStats] = useState(null);
  const [tlsStats, setTlsStats] = useState(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const rtpRes = await fetch(`${API_BASE}/api/rtp/stats`);
        const rtpData = await rtpRes.json();
        setRtpStats(rtpData);
      } catch (e) {
        console.error("Error fetching RTP stats:", e);
      }

      try {
        const tlsRes = await fetch(`${API_BASE}/api/security/tls`);
        const tlsData = await tlsRes.json();
        setTlsStats(tlsData);
      } catch (e) {
        console.error("Error fetching TLS stats:", e);
      }
    };

    fetchData();
    const timer = setInterval(fetchData, 1000);
    return () => clearInterval(timer);
  }, [API_BASE]);

  const isAudioActive = rtpStats && rtpStats.audio_running;
  const isTlsActive = tlsStats && tlsStats.protocol && tlsStats.protocol !== 'None';

  return (
    <div className="diagnostics-view" style={{ display: 'flex', flexDirection: 'column', gap: '20px', height: '100%', overflowY: 'auto', paddingRight: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold' }}>RTP Stream Inspector & Security Diagnostics</h3>
        <span style={{
          padding: '4px 10px',
          borderRadius: '4px',
          fontSize: '12px',
          fontWeight: 'bold',
          background: isAudioActive ? 'rgba(74, 222, 128, 0.1)' : 'rgba(239, 68, 68, 0.1)',
          color: isAudioActive ? 'var(--accent-green)' : 'var(--accent-red)',
          border: `1px solid ${isAudioActive ? 'var(--accent-green)' : 'var(--accent-red)'}`
        }}>
          {isAudioActive ? 'Status: Live Audio Streaming' : 'Status: No Active Audio Stream'}
        </span>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div style={{
            background: 'rgba(255, 255, 255, 0.01)',
            border: '1px solid var(--border-color)',
            borderRadius: '12px',
            padding: '20px'
          }}>
            <h4 style={{ margin: '0 0 15px 0', fontSize: '13px', color: 'var(--accent-cyan)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
              Transmit RTP (Local → Remote)
            </h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', borderBottom: '1px solid rgba(255,255,255,0.03)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-secondary)' }}>SSRC</span>
                <span style={{ fontFamily: 'Fira Code, monospace', fontWeight: '600' }}>
                  {isAudioActive && rtpStats.tx_ssrc !== undefined && rtpStats.tx_ssrc !== -1 ? `0x${rtpStats.tx_ssrc.toString(16).toUpperCase()}` : '-'}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', borderBottom: '1px solid rgba(255,255,255,0.03)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Payload Type</span>
                <span>{isAudioActive && rtpStats.tx_payload_type !== undefined && rtpStats.tx_payload_type !== -1 ? rtpStats.tx_payload_type : '-'}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', borderBottom: '1px solid rgba(255,255,255,0.03)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Packets Sent</span>
                <span>{isAudioActive && rtpStats.sender ? rtpStats.sender.packets_sent : '-'}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Bytes Sent</span>
                <span>{isAudioActive && rtpStats.sender ? rtpStats.sender.bytes_sent : '-'}</span>
              </div>
            </div>
          </div>

          <div style={{
            background: 'rgba(255, 255, 255, 0.01)',
            border: '1px solid var(--border-color)',
            borderRadius: '12px',
            padding: '20px'
          }}>
            <h4 style={{ margin: '0 0 15px 0', fontSize: '13px', color: 'var(--accent-green)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
              Receive RTP (Remote → Local)
            </h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', borderBottom: '1px solid rgba(255,255,255,0.03)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-secondary)' }}>SSRC</span>
                <span style={{ fontFamily: 'Fira Code, monospace', fontWeight: '600' }}>
                  {isAudioActive && rtpStats.rx_ssrc !== undefined && rtpStats.rx_ssrc !== -1 ? `0x${rtpStats.rx_ssrc.toString(16).toUpperCase()}` : '-'}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', borderBottom: '1px solid rgba(255,255,255,0.03)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Payload Type</span>
                <span>{isAudioActive && rtpStats.rx_payload_type !== undefined && rtpStats.rx_payload_type !== -1 ? rtpStats.rx_payload_type : '-'}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', borderBottom: '1px solid rgba(255,255,255,0.03)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Packets Received</span>
                <span>{isAudioActive && rtpStats.receiver ? rtpStats.receiver.packets_received : '-'}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', borderBottom: '1px solid rgba(255,255,255,0.03)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Bytes Received</span>
                <span>{isAudioActive && rtpStats.receiver ? rtpStats.receiver.bytes_received : '-'}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', borderBottom: '1px solid rgba(255,255,255,0.03)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Packets Lost/Dropped</span>
                <span style={{ color: isAudioActive && rtpStats.dropped_packets > 0 ? 'var(--accent-red)' : 'inherit' }}>
                  {isAudioActive ? rtpStats.dropped_packets : '-'}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', borderBottom: '1px solid rgba(255,255,255,0.03)', paddingBottom: '6px' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Jitter</span>
                <span>{isAudioActive && rtpStats.rx_jitter_ms !== undefined ? `${rtpStats.rx_jitter_ms.toFixed(2)} ms` : '-'}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Timestamp Drift</span>
                <span>{isAudioActive && rtpStats.rx_drift_ms !== undefined ? `${rtpStats.rx_drift_ms} ms` : '-'}</span>
              </div>
            </div>
          </div>
        </div>

        <div style={{
          background: 'rgba(255, 255, 255, 0.01)',
          border: '1px solid var(--border-color)',
          borderRadius: '12px',
          padding: '20px',
          display: 'flex',
          flexDirection: 'column',
          gap: '15px'
        }}>
          <h4 style={{ margin: 0, fontSize: '13px', color: '#f43f5e', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
            SIP Security & SRTP Details
          </h4>
          
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', borderBottom: '1px solid rgba(255,255,255,0.03)', paddingBottom: '6px' }}>
              <span style={{ color: 'var(--text-secondary)' }}>TLS Version</span>
              <span style={{ fontWeight: '600', color: isTlsActive ? 'var(--accent-green)' : 'inherit' }}>
                {isTlsActive ? tlsStats.protocol : 'No active TLS connection'}
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', borderBottom: '1px solid rgba(255,255,255,0.03)', paddingBottom: '6px' }}>
              <span style={{ color: 'var(--text-secondary)' }}>Negotiated Cipher</span>
              <span style={{ fontFamily: 'Fira Code, monospace', fontSize: '11px' }}>
                {isTlsActive ? tlsStats.cipher_suite : '-'}
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px' }}>
              <span style={{ color: 'var(--text-secondary)' }}>SRTP SDES Suite</span>
              <span style={{
                color: isTlsActive && tlsStats.srtp_cipher !== 'Disabled (Plain RTP)' ? 'var(--accent-green)' : 'var(--text-secondary)',
                fontWeight: '600'
              }}>
                {isTlsActive ? tlsStats.srtp_cipher : '-'}
              </span>
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', flex: 1 }}>
            <span style={{ fontSize: '12px', fontWeight: 'bold', color: 'var(--text-secondary)' }}>Server SSL Certificate details:</span>
            <div style={{
              flex: 1,
              background: '#07090e',
              border: '1px solid var(--border-color)',
              borderRadius: '8px',
              padding: '12px',
              fontFamily: 'Fira Code, monospace',
              fontSize: '11px',
              color: '#cbd5e1',
              whiteSpace: 'pre-wrap',
              overflowY: 'auto',
              minHeight: '120px'
            }}>
              {isTlsActive ? (
                `Subject: ${tlsStats.peer_cert_subject}\nIssuer: ${tlsStats.peer_cert_issuer}\nValidity: ${tlsStats.peer_cert_validity}`
              ) : (
                'SSL details will be populated when a TLS call scenario executes.'
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function App() {
  // Settings & Status state
  const [settings, setSettings] = useState({
    serversInfo: {
      localIPAddress: '',
      localPort: '',
      remoteIPAddress: '',
      remotePort: ''
    },
    account: {
      username: ''
    }
  });
  
  const [logFiles, setLogFiles] = useState([]);
  const [selectedLog, setSelectedLog] = useState(null);
  const [logContent, setLogContent] = useState('');
  
  const [recordings, setRecordings] = useState([]);
  const [selectedRecording, setSelectedRecording] = useState(null);
  
  // UI states
  const [activeTab, setActiveTab] = useState('console'); // 'console', 'logs', 'recordings', 'editor'
  const [consoleOutput, setConsoleOutput] = useState([]);
  const [isRunning, setIsRunning] = useState(false);
  const [runType, setRunType] = useState(null); // 'register' or 'call'
  const [saveSuccess, setSaveSuccess] = useState(false);
  
  // Editor states
  const [editorFileType, setEditorFileType] = useState('scenarios'); // 'scenarios' or 'values'
  const [editorFileName, setEditorFileName] = useState('');
  const [editorContent, setEditorContent] = useState('');
  const [editorLoading, setEditorLoading] = useState(false);
  const [editorSaveSuccess, setEditorSaveSuccess] = useState(false);
  const [editorError, setEditorError] = useState('');
  const [customHeaderName, setCustomHeaderName] = useState('');
  const [customHeaderValue, setCustomHeaderValue] = useState('');
  const textareaRef = useRef(null);
  const [editorMatchingValues, setEditorMatchingValues] = useState('');
  const [showPreviewPane, setShowPreviewPane] = useState(true);
  const fileInputRef = useRef(null);

  // Logs filtering state
  const [logFilterText, setLogFilterText] = useState('');
  
  // SIP Ladder state
  const [logViewMode, setLogViewMode] = useState('text'); // 'text' or 'ladder'
  const [ladderData, setLadderData] = useState([]);
  const [ladderLoading, setLadderLoading] = useState(false);
  const [selectedLadderMsg, setSelectedLadderMsg] = useState(null);

  // Campaign Runner state
  const [campaigns, setCampaigns] = useState([]);
  const [campaignRunning, setCampaignRunning] = useState(false);
  const [currentCampaignIndex, setCurrentCampaignIndex] = useState(-1);
  const [campaignOutput, setCampaignOutput] = useState([]);
  const [showAddCampaignModal, setShowAddCampaignModal] = useState(false);
  const [newCampaignItem, setNewCampaignItem] = useState({
    name: 'OXE Trunk Registration Test',
    callerScenario: 'Reg_client.txt',
    callerValues: 'Reg_client_values.txt',
    calleeScenario: 'Incoming_basic_call_generic.txt',
    calleeValues: 'callee_values.txt',
    expectedStatus: 'PASS' // PASS or FAIL
  });

  // Simulation Control states
  const [simData, setSimData] = useState({
    delay: '0',
    loss: '0',
    malformed: 'None',
    regState: 'Unregistered',
    dialogCount: 0
  });

  const fetchSimulationData = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/simulation`);
      const data = await res.json();
      setSimData(data);
    } catch (e) {
      console.error("Error fetching simulation data:", e);
    }
  };

  const saveSimulationSettings = async (updates) => {
    try {
      const res = await fetch(`${API_BASE}/api/simulation`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updates)
      });
      const data = await res.json();
      if (data.success) {
        setSimData(prev => ({ ...prev, ...updates }));
      }
    } catch (e) {
      console.error("Error saving simulation settings:", e);
    }
  };
  
  // Form states
  const [formSettings, setFormSettings] = useState({
    localIPAddress: '',
    localPort: '',
    remoteIPAddress: '',
    remotePort: '',
    username: ''
  });

  const consoleEndRef = useRef(null);
  const eventSourceRef = useRef(null);

  const [availableScenarios, setAvailableScenarios] = useState([]);
  const [availableValues, setAvailableValues] = useState([]);
  const [scenariosConfig, setScenariosConfig] = useState({
    callerScenario: 'Outgoing_basic_call.txt',
    callerValues: 'Outgoing_basic_call_values.txt',
    calleeScenario: 'Incoming_basic_call_generic.txt',
    calleeValues: 'callee_values.txt'
  });

  const handleScenarioConfigChange = (e) => {
    const { name, value } = e.target;
    setScenariosConfig(prev => {
      const updated = { ...prev, [name]: value };
      
      // Auto-matching logic
      if (name === 'callerScenario') {
        const base = value.replace('.txt', '');
        const matching = `${base}_values.txt`;
        const exists = availableValues.some(v => v.toLowerCase() === matching.toLowerCase());
        if (exists) {
          const found = availableValues.find(v => v.toLowerCase() === matching.toLowerCase());
          updated.callerValues = found;
        }
      } else if (name === 'calleeScenario') {
        const base = value.replace('.txt', '');
        const matching = `${base}_values.txt`;
        const exists = availableValues.some(v => v.toLowerCase() === matching.toLowerCase());
        if (exists) {
          const found = availableValues.find(v => v.toLowerCase() === matching.toLowerCase());
          updated.calleeValues = found;
        }
      }
      return updated;
    });
  };

  const fetchScenariosAndValues = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/scenarios-values`);
      const data = await res.json();
      setAvailableScenarios(data.scenarios || []);
      setAvailableValues(data.values || []);
    } catch (e) {
      console.error("Error fetching scenarios/values:", e);
    }
  };

  // Fetch status and settings on mount
  useEffect(() => {
    fetchStatus();
    fetchScenariosAndValues();
    fetchSimulationData();
    const interval = setInterval(() => {
      fetchStatus();
      fetchSimulationData();
    }, 2000);
    return () => clearInterval(interval);
  }, []);

  // Scroll console to bottom when new logs arrive
  useEffect(() => {
    if (consoleEndRef.current) {
      consoleEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [consoleOutput]);

  const fetchRecordings = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/recordings`);
      const data = await res.json();
      setRecordings(data);
    } catch (e) {
      console.error("Error fetching recordings:", e);
    }
  };

  const fetchFileContent = async (type, filename) => {
    if (!filename) {
      setEditorContent('');
      setEditorMatchingValues('');
      return;
    }
    setEditorLoading(true);
    setEditorError('');
    try {
      const res = await fetch(`${API_BASE}/api/files/${type}/${filename}`);
      const data = await res.json();
      if (data.content) {
        setEditorContent(data.content);
        if (type === 'scenarios') {
          const valName = filename.replace('.txt', '_values.txt');
          try {
            const valRes = await fetch(`${API_BASE}/api/files/values/${valName}`);
            const valData = await valRes.json();
            if (valData.content) {
              setEditorMatchingValues(valData.content);
            } else {
              setEditorMatchingValues('');
            }
          } catch (e) {
            setEditorMatchingValues('');
          }
        } else {
          setEditorMatchingValues('');
        }
      } else if (data.error) {
        setEditorError(data.error);
      }
    } catch (e) {
      setEditorError('Failed to load file content.');
      console.error(e);
    } finally {
      setEditorLoading(false);
    }
  };

  const handlePcapUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    
    setEditorLoading(true);
    setEditorError('');
    
    const callId = prompt("Enter specific Call-ID to extract (leave empty to convert the most active call):") || '';
    const nameBase = file.name.replace(/\.[^/.]+$/, "").replace(/[^a-zA-Z0-9_]/g, "_");
    
    const scenarioName = prompt("Enter target Scenario file name:", `${nameBase}_scenario.txt`) || `${nameBase}_scenario.txt`;
    const valuesName = prompt("Enter target Values file name:", `${nameBase}_values.txt`) || `${nameBase}_values.txt`;

    try {
      const reader = new FileReader();
      reader.onload = async (event) => {
        const arrayBuffer = event.target.result;
        try {
          const res = await fetch(`${API_BASE}/api/files/import-pcap?callId=${encodeURIComponent(callId)}&scenarioName=${encodeURIComponent(scenarioName)}&valuesName=${encodeURIComponent(valuesName)}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/octet-stream' },
            body: arrayBuffer
          });
          const data = await res.json();
          if (data.success) {
            alert(`PCAP imported successfully!\nGenerated:\n- scenarios/${data.scenarioName}\n- values/${data.valuesName}`);
            await fetchScenariosAndValues();
            setEditorFileType('scenarios');
            setEditorFileName(data.scenarioName);
          } else {
            setEditorError(data.error || 'PCAP conversion failed.');
          }
        } catch (err) {
          setEditorError('Failed to upload and parse PCAP.');
          console.error(err);
        } finally {
          setEditorLoading(false);
        }
      };
      reader.readAsArrayBuffer(file);
    } catch (err) {
      setEditorError('Failed to read local PCAP file.');
      setEditorLoading(false);
    }
  };

  const saveFileContent = async () => {
    if (!editorFileName) return;
    setEditorLoading(true);
    setEditorError('');
    try {
      const res = await fetch(`${API_BASE}/api/files/${editorFileType}/${editorFileName}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: editorContent })
      });
      const data = await res.json();
      if (data.success) {
        setEditorSaveSuccess(true);
        setTimeout(() => setEditorSaveSuccess(false), 3000);
        fetchScenariosAndValues();
      } else if (data.error) {
        setEditorError(data.error);
      }
    } catch (e) {
      setEditorError('Failed to save file content.');
      console.error(e);
    } finally {
      setEditorLoading(false);
    }
  };

  const injectHeader = (headerText) => {
    const textarea = textareaRef.current;
    if (!textarea) return;
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const currentText = editorContent;
    const newText = currentText.substring(0, start) + headerText + currentText.substring(end);
    setEditorContent(newText);
    
    setTimeout(() => {
      textarea.focus();
      textarea.setSelectionRange(start + headerText.length, start + headerText.length);
    }, 0);
  };

  useEffect(() => {
    if (editorFileName) {
      fetchFileContent(editorFileType, editorFileName);
    } else {
      setEditorContent('');
    }
  }, [editorFileType, editorFileName]);

  useEffect(() => {
    if (editorFileType === 'scenarios' && availableScenarios.length > 0) {
      setEditorFileName(availableScenarios[0]);
    } else if (editorFileType === 'values' && availableValues.length > 0) {
      setEditorFileName(availableValues[0]);
    } else {
      setEditorFileName('');
    }
  }, [editorFileType, availableScenarios, availableValues]);

  const fetchLadderData = async (filename) => {
    if (!filename) return;
    setLadderLoading(true);
    setSelectedLadderMsg(null);
    try {
      const res = await fetch(`${API_BASE}/api/logs/${filename}/ladder`);
      const data = await res.json();
      setLadderData(data.messages || []);
    } catch (e) {
      console.error("Error fetching ladder data:", e);
      setLadderData([]);
    } finally {
      setLadderLoading(false);
    }
  };

  useEffect(() => {
    if (selectedLog && logViewMode === 'ladder') {
      fetchLadderData(selectedLog);
    }
  }, [selectedLog, logViewMode]);

  const fetchCampaigns = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/campaigns`);
      const data = await res.json();
      setCampaigns(data || []);
    } catch (e) {
      console.error("Error fetching campaigns:", e);
    }
  };

  const saveCampaigns = async (updatedCampaigns) => {
    try {
      await fetch(`${API_BASE}/api/campaigns`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updatedCampaigns)
      });
      setCampaigns(updatedCampaigns);
    } catch (e) {
      console.error("Error saving campaigns:", e);
    }
  };

  useEffect(() => {
    fetchCampaigns();
  }, []);

  const stopCampaignRef = useRef(false);

  const executeTestCase = (testCase) => {
    return new Promise((resolve) => {
      const startTime = Date.now();
      let logsBuffer = [];
      let isCompleted = false;

      // Construct endpoint (determine if it is register or call test)
      const isRegister = testCase.callerScenario.toLowerCase().includes('reg');
      const endpoint = isRegister
        ? '/api/stream/register'
        : `/api/stream/call?callerScenario=${encodeURIComponent(testCase.callerScenario)}&callerValues=${encodeURIComponent(testCase.callerValues)}&calleeScenario=${encodeURIComponent(testCase.calleeScenario)}&calleeValues=${encodeURIComponent(testCase.calleeValues)}`;
      
      const source = new EventSource(`${API_BASE}${endpoint}`);

      source.onmessage = (event) => {
        logsBuffer.push(event.data);
        setCampaignOutput(prev => [...prev, `[LOGS] ${event.data}`]);
      };

      const finishTest = () => {
        if (isCompleted) return;
        isCompleted = true;
        source.close();
        const duration = ((Date.now() - startTime) / 1000).toFixed(1);

        // Analyze logs buffer for test result PASS/FAIL
        let pass = false;
        const hasError = logsBuffer.some(line => line.includes('[ERROR]') || line.includes('failed') || line.includes('java.io.IOException'));
        
        if (isRegister) {
          const hasOk = logsBuffer.some(line => line.includes('SIP/2.0 200 OK') || line.includes('Registered successfully'));
          if (!hasError && hasOk) {
            pass = true;
          }
        } else {
          const hasInvite = logsBuffer.some(line => line.includes('INVITE sip:'));
          const hasOk = logsBuffer.some(line => line.includes('200 OK'));
          const hasBye = logsBuffer.some(line => line.includes('BYE sip:'));
          if (!hasError && (hasInvite || hasOk || hasBye)) {
            pass = true;
          }
        }

        const runOutcome = pass ? 'PASS' : 'FAIL';
        const isPassExpected = runOutcome === testCase.expectedStatus;

        resolve({
          pass: isPassExpected,
          duration,
        });
      };

      source.onerror = () => {
        finishTest();
      };
    });
  };

  const startCampaignExecution = async () => {
    if (campaigns.length === 0 || campaignRunning) return;
    setCampaignRunning(true);
    stopCampaignRef.current = false;
    setCampaignOutput([`[SYSTEM] Starting batch test campaign containing ${campaigns.length} test cases...`]);
    
    let currentResults = campaigns.map(c => ({ ...c, runStatus: 'Pending', runResult: '' }));
    setCampaigns(currentResults);

    for (let i = 0; i < currentResults.length; i++) {
      if (stopCampaignRef.current) {
        setCampaignOutput(prev => [...prev, `[SYSTEM] Campaign aborted by user.`]);
        break;
      }

      setCurrentCampaignIndex(i);
      currentResults[i].runStatus = 'Running';
      setCampaigns([...currentResults]);

      const testCase = currentResults[i];
      setCampaignOutput(prev => [...prev, `[SYSTEM] [TestCase ${i+1}/${currentResults.length}] Executing: ${testCase.name}...`]);

      const outcome = await executeTestCase(testCase);

      if (stopCampaignRef.current) {
        currentResults[i].runStatus = 'Aborted';
        setCampaigns([...currentResults]);
        break;
      }

      currentResults[i].runStatus = 'Completed';
      currentResults[i].runResult = outcome.pass ? 'PASS' : 'FAIL';
      currentResults[i].duration = outcome.duration;
      setCampaigns([...currentResults]);

      setCampaignOutput(prev => [
        ...prev, 
        `[SYSTEM] Finished: ${testCase.name} -> ${outcome.pass ? 'PASS' : 'FAIL'} (Duration: ${outcome.duration}s)`
      ]);

      await new Promise(resolve => setTimeout(resolve, 2000));
    }

    setCampaignRunning(false);
    setCurrentCampaignIndex(-1);
    saveCampaigns(currentResults);
    fetchStatus();
  };

  const stopCampaignExecution = () => {
    stopCampaignRef.current = true;
    setCampaignRunning(false);
    setCurrentCampaignIndex(-1);
    stopTest();
  };

  const fetchStatus = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/status`);
      const data = await res.json();
      if (data.settings) {
        setSettings(data.settings);
        // Initialize form fields if not touched
        setFormSettings(prev => ({
          localIPAddress: prev.localIPAddress || data.settings.serversInfo?.localIPAddress || '',
          localPort: prev.localPort || data.settings.serversInfo?.localPort || '',
          remoteIPAddress: prev.remoteIPAddress || data.settings.serversInfo?.remoteIPAddress || '',
          remotePort: prev.remotePort || data.settings.serversInfo?.remotePort || '',
          username: prev.username || data.settings.account?.username || ''
        }));
      }
      if (data.logFiles) {
        setLogFiles(data.logFiles);
      }
      fetchRecordings();
    } catch (e) {
      console.error("Error fetching status:", e);
    }
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormSettings(prev => ({ ...prev, [name]: value }));
  };

  const saveSettings = async (e) => {
    e.preventDefault();
    try {
      const res = await fetch(`${API_BASE}/api/settings`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formSettings)
      });
      const data = await res.json();
      if (data.success) {
        setSaveSuccess(true);
        setTimeout(() => setSaveSuccess(false), 3000);
        fetchStatus();
      }
    } catch (e) {
      console.error("Error saving settings:", e);
    }
  };

  // Trigger test via SSE stream
  const startTest = (type) => {
    if (isRunning) return;
    
    setIsRunning(true);
    setRunType(type);
    setActiveTab('console');
    setConsoleOutput([`[SYSTEM] Connecting to server-sent events for: ${type}...`]);

    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    const endpoint = type === 'register' 
      ? '/api/stream/register' 
      : `/api/stream/call?callerScenario=${encodeURIComponent(scenariosConfig.callerScenario)}&callerValues=${encodeURIComponent(scenariosConfig.callerValues)}&calleeScenario=${encodeURIComponent(scenariosConfig.calleeScenario)}&calleeValues=${encodeURIComponent(scenariosConfig.calleeValues)}`;
    const source = new EventSource(`${API_BASE}${endpoint}`);
    eventSourceRef.current = source;

    source.onmessage = (event) => {
      setConsoleOutput(prev => [...prev, event.data]);
    };

    source.onerror = (err) => {
      setConsoleOutput(prev => [...prev, '[SYSTEM] Connection closed or finished.']);
      source.close();
      setIsRunning(false);
      setRunType(null);
      fetchStatus();
    };
  };

  const stopTest = async () => {
    try {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
      const res = await fetch(`${API_BASE}/api/test/stop`, { method: 'POST' });
      const data = await res.json();
      setConsoleOutput(prev => [...prev, `[SYSTEM] ${data.message}`]);
      setIsRunning(false);
      setRunType(null);
      fetchStatus();
    } catch (e) {
      console.error("Error stopping test:", e);
    }
  };

  const viewLogContent = async (filename) => {
    try {
      setSelectedLog(filename);
      const res = await fetch(`${API_BASE}/api/logs/${filename}`);
      const data = await res.json();
      setLogContent(data.content || 'Empty file');
    } catch (e) {
      console.error("Error fetching log content:", e);
      setLogContent('Failed to load log file');
    }
  };

  // Helper to colorize lines inside console output
  const formatConsoleLine = (line) => {
    if (line.startsWith('[ERROR]')) {
      return <span className="line-error">{line}</span>;
    }
    if (line.startsWith('[SYSTEM]')) {
      return <span className="line-system">{line}</span>;
    }
    if (line.startsWith('[CALLER]')) {
      return <span className="line-caller">{line}</span>;
    }
    if (line.startsWith('[CALLEE]')) {
      return <span className="line-callee">{line}</span>;
    }
    return <span>{line}</span>;
  };

  // Helper to colorize SIP messages inside log viewer
  const formatLogContent = (content) => {
    return content.split('\n').map((line, idx) => {
      let className = '';
      if (line.includes('REGISTER sip:') || line.includes('INVITE sip:') || line.includes('BYE sip:') || line.includes('ACK sip:')) {
        className = 'log-sip-send';
      } else if (line.includes('SIP/2.0 200 OK') || line.includes('SIP/2.0 180 Ringing') || line.includes('SIP/2.0 100 Trying')) {
        className = 'log-sip-recv';
      } else if (line.includes('[RTP TX]')) {
        className = 'log-rtp-tx';
      } else if (line.includes('activeSenders=') || line.includes('LiveAudioDiag')) {
        className = 'log-diag';
      }
      return <div key={idx} className={className}>{line}</div>;
    });
  };

  return (
    <div className="app-container">
      {/* Sidebar / Settings */}
      <aside className="sidebar">
        <div className="brand">
          <div className="logo-cube"></div>
          <h2>SIP Simulator <span className="v4-badge">v4</span></h2>
        </div>

        <section className="settings-section">
          <h3>Network Settings</h3>
          <form onSubmit={saveSettings}>
            <div className="input-group">
              <label>Local IP Address</label>
              <input 
                type="text" 
                name="localIPAddress" 
                value={formSettings.localIPAddress} 
                onChange={handleInputChange}
                required
              />
            </div>
            
            <div className="input-group">
              <label>Local SIP Port</label>
              <input 
                type="number" 
                name="localPort" 
                value={formSettings.localPort} 
                onChange={handleInputChange}
                required
              />
            </div>

            <div className="input-group">
              <label>Remote IP Address</label>
              <input 
                type="text" 
                name="remoteIPAddress" 
                value={formSettings.remoteIPAddress} 
                onChange={handleInputChange}
                required
              />
            </div>

            <div className="input-group">
              <label>Remote SIP Port</label>
              <input 
                type="number" 
                name="remotePort" 
                value={formSettings.remotePort} 
                onChange={handleInputChange}
                required
              />
            </div>

            <div className="input-group">
              <label>Default Username (Extension)</label>
              <input 
                type="text" 
                name="username" 
                value={formSettings.username} 
                onChange={handleInputChange}
                required
              />
            </div>

            <button type="submit" className="btn-save" disabled={isRunning}>
              Apply Network Configurations
            </button>
            
            {saveSuccess && <div className="save-toast">✓ Settings updated!</div>}
          </form>
        </section>

        <section className="settings-section">
          <h3>Scenario & Values Selection</h3>
          
          <CustomSelect
            label="Caller Scenario"
            options={availableScenarios}
            value={scenariosConfig.callerScenario}
            onChange={(val) => handleScenarioConfigChange({ target: { name: 'callerScenario', value: val } })}
            disabled={isRunning}
          />
          
          <CustomSelect
            label="Caller Values"
            options={availableValues}
            value={scenariosConfig.callerValues}
            onChange={(val) => handleScenarioConfigChange({ target: { name: 'callerValues', value: val } })}
            disabled={isRunning}
          />

          <CustomSelect
            label="Callee Scenario"
            options={availableScenarios}
            value={scenariosConfig.calleeScenario}
            onChange={(val) => handleScenarioConfigChange({ target: { name: 'calleeScenario', value: val } })}
            disabled={isRunning}
          />

          <CustomSelect
            label="Callee Values"
            options={availableValues}
            value={scenariosConfig.calleeValues}
            onChange={(val) => handleScenarioConfigChange({ target: { name: 'calleeValues', value: val } })}
            disabled={isRunning}
          />
        </section>

        <section className="quick-actions">
          <h3>Test Suite Controller</h3>
          <button 
            className={`btn-action btn-register ${runType === 'register' ? 'active' : ''}`}
            onClick={() => startTest('register')}
            disabled={isRunning}
          >
            {runType === 'register' ? 'Registering...' : 'Run Registration Test'}
          </button>
          
          <button 
            className={`btn-action btn-call ${runType === 'call' ? 'active' : ''}`}
            onClick={() => startTest('call')}
            disabled={isRunning}
          >
            {runType === 'call' ? 'Call Active...' : 'Run E2E Call Test (PCMA RTP)'}
          </button>

          {isRunning && (
            <button className="btn-action btn-stop" onClick={stopTest}>
              ■ Stop Active Executions
            </button>
          )}
        </section>
      </aside>

      {/* Main Panel */}
      <main className="main-content">
        <header className="main-header">
          <div className="status-bar-web">
            <div className="status-item">
              <span className="dot dot-green"></span>
              <strong>Server status:</strong> Active
            </div>
            <div className="status-item">
              <strong>Local Binding:</strong> {settings.serversInfo?.localIPAddress}:{settings.serversInfo?.localPort}
            </div>
            <div className="status-item">
              <strong>Remote Server:</strong> {settings.serversInfo?.remoteIPAddress}:{settings.serversInfo?.remotePort}
            </div>
            <div className="status-item">
              <strong>Registration:</strong> <span style={{ color: simData.regState === 'Registered' ? 'var(--accent-green)' : (simData.regState === 'Registering...' ? 'var(--accent-cyan)' : 'var(--accent-red)') }}>{simData.regState}</span>
            </div>
            <div className="status-item">
              <strong>Active Dialogs:</strong> <span style={{ color: simData.dialogCount > 0 ? 'var(--accent-green)' : 'var(--text-secondary)' }}>{simData.dialogCount} active</span>
            </div>
          </div>
        </header>

        {/* Tab Controls */}
        <div className="tab-container">
          <div className="tabs">
            <button 
              className={`tab-btn ${activeTab === 'console' ? 'active' : ''}`} 
              onClick={() => { console.log('Console tab clicked!'); setActiveTab('console'); }}
            >
              Execution Console
            </button>
            <button 
              className={`tab-btn ${activeTab === 'logs' ? 'active' : ''}`} 
              onClick={() => { console.log('Logs tab clicked!'); setActiveTab('logs'); }}
            >
              UDP Logs Browser ({logFiles.length})
            </button>
            <button 
              className={`tab-btn ${activeTab === 'recordings' ? 'active' : ''}`} 
              onClick={() => { console.log('Recordings tab clicked!'); setActiveTab('recordings'); }}
            >
              RTP Audio Recordings ({recordings.length})
            </button>
            <button 
              className={`tab-btn ${activeTab === 'diagnostics' ? 'active' : ''}`} 
              onClick={() => { console.log('Diagnostics tab clicked!'); setActiveTab('diagnostics'); }}
            >
              Media & Security Diagnostics
            </button>
            <button 
              className={`tab-btn ${activeTab === 'editor' ? 'active' : ''}`} 
              onClick={() => { console.log('Editor tab clicked!'); setActiveTab('editor'); }}
            >
              SIP Template & Values Customizer
            </button>
            <button 
              className={`tab-btn ${activeTab === 'campaigns' ? 'active' : ''}`} 
              onClick={() => { console.log('Campaigns tab clicked!'); setActiveTab('campaigns'); }}
            >
              Test Campaign Runner ({campaigns.length})
            </button>
            <button 
              className={`tab-btn ${activeTab === 'simulation' ? 'active' : ''}`} 
              onClick={() => { console.log('Simulation tab clicked!'); setActiveTab('simulation'); }}
            >
              Simulation Control
            </button>
          </div>
        </div>

        {/* Tab Content */}
        <div className="content-pane">
          {activeTab === 'console' && (
            <div className="console-view">
              <div className="console-header">
                <span>Console Out (Java Stdout Streams)</span>
                <button onClick={() => setConsoleOutput([])} className="btn-clear-console">Clear</button>
              </div>
              <div className="console-body">
                {consoleOutput.length === 0 ? (
                  <div className="console-empty">Console idle. Trigger a test to see output streams.</div>
                ) : (
                  consoleOutput.map((line, idx) => (
                    <div key={idx} className="console-line">
                      {formatConsoleLine(line)}
                    </div>
                  ))
                )}
                <div ref={consoleEndRef} />
              </div>
            </div>
          )}

          {activeTab === 'diagnostics' && (
            <RtpDiagnosticsTab API_BASE={API_BASE} />
          )}

          {activeTab === 'logs' && (
            <div className="logs-view">
              {/* Logs file list */}
              <div className="logs-sidebar">
                <h3>Select Session Log</h3>
                <input
                  type="text"
                  placeholder="Filter logs by Call-ID, name..."
                  value={logFilterText}
                  onChange={(e) => setLogFilterText(e.target.value)}
                  style={{
                    width: '100%',
                    marginBottom: '12px',
                    padding: '8px 12px',
                    background: 'rgba(0, 0, 0, 0.2)',
                    border: '1px solid var(--border-color)',
                    color: '#fff',
                    fontSize: '12px',
                    borderRadius: '4px',
                    outline: 'none'
                  }}
                />
                <div className="logs-list">
                  {logFiles.filter(file => file.name.toLowerCase().includes(logFilterText.toLowerCase())).length === 0 ? (
                    <div className="logs-empty">No matching log files.</div>
                  ) : (
                    logFiles.filter(file => file.name.toLowerCase().includes(logFilterText.toLowerCase())).map((file, idx) => (
                      <button 
                        key={idx}
                        className={`log-item-btn ${selectedLog === file.name ? 'active' : ''}`}
                        onClick={() => { viewLogContent(file.name); if (logViewMode === 'ladder') fetchLadderData(file.name); }}
                      >
                        <div className="log-item-title">{file.name}</div>
                        <div className="log-item-meta">
                          {(file.size / 1024).toFixed(1)} KB | {new Date(file.mtime).toLocaleTimeString()}
                        </div>
                      </button>
                    ))
                  )}
                </div>
              </div>

              {/* Log contents */}
              <div className="log-content-panel">
                <div className="log-content-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span>{selectedLog ? `Viewing: ${selectedLog}` : 'Select a log file to view'}</span>
                  {selectedLog && (
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <button 
                        className={`tab-btn ${logViewMode === 'text' ? 'active' : ''}`}
                        onClick={() => setLogViewMode('text')}
                        style={{ padding: '4px 10px', fontSize: '11px' }}
                      >
                        Raw Log Text
                      </button>
                      <button 
                        className={`tab-btn ${logViewMode === 'ladder' ? 'active' : ''}`}
                        onClick={() => { setLogViewMode('ladder'); fetchLadderData(selectedLog); }}
                        style={{ padding: '4px 10px', fontSize: '11px' }}
                      >
                        Visual SIP Ladder
                      </button>
                    </div>
                  )}
                </div>
                <div className="log-content-body">
                  {selectedLog ? (
                    logViewMode === 'text' ? (
                      <pre className="log-content-pre">
                        {formatLogContent(logContent)}
                      </pre>
                    ) : (
                      <div style={{ display: 'flex', height: '100%', gap: '15px', overflow: 'hidden' }}>
                        <div style={{ flex: 1, height: '100%', overflow: 'hidden' }}>
                          {ladderLoading ? (
                            <div className="console-empty">Loading visual ladder...</div>
                          ) : (
                            <SipLadderDiagram 
                              data={ladderData} 
                              onSelectMessage={(msg) => setSelectedLadderMsg(msg)}
                              selectedMsg={selectedLadderMsg}
                            />
                          )}
                        </div>
                        {selectedLadderMsg && (
                          <div style={{ width: '350px', background: '#0a0e17', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '16px', display: 'flex', flexDirection: 'column', gap: '10px', overflowY: 'auto' }}>
                            <h4 style={{ fontSize: '12px', textTransform: 'uppercase', color: 'var(--accent-cyan)', borderBottom: '1px solid var(--border-color)', paddingBottom: '6px', margin: 0, display: 'flex', justifyContent: 'space-between' }}>
                              <span>{selectedLadderMsg.label} DETAIL</span>
                              <button onClick={() => setSelectedLadderMsg(null)} style={{ background: 'none', border: 'none', color: '#ff3131', cursor: 'pointer', fontSize: '10px' }}>CLOSE</button>
                            </h4>
                            <div style={{ fontSize: '11px', color: 'var(--text-secondary)', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                              <div><strong>Source:</strong> {selectedLadderMsg.source}</div>
                              <div><strong>Target:</strong> {selectedLadderMsg.target}</div>
                              <div><strong>Call-ID:</strong> {selectedLadderMsg.callId || 'None'}</div>
                              <div><strong>CSeq:</strong> {selectedLadderMsg.cseq || 'None'}</div>
                            </div>
                            <pre style={{ margin: 0, padding: '10px', background: '#05070a', border: '1px solid rgba(255,255,255,0.05)', borderRadius: '6px', color: '#f1f3f9', fontFamily: 'Fira Code, monospace', fontSize: '11px', whiteSpace: 'pre-wrap', lineHeight: '1.4' }}>
                              {selectedLadderMsg.rawText}
                            </pre>
                          </div>
                        )}
                      </div>
                    )
                  ) : (
                    <div className="log-content-empty">
                      Click a log session file on the left side to display color-coded SIP signaling diagrams or sequence ladders.
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {activeTab === 'recordings' && (
            <div className="recordings-view">
              {/* Recordings sidebar */}
              <div className="logs-sidebar">
                <h3>Select Call Audio</h3>
                <div className="logs-list">
                  {recordings.length === 0 ? (
                    <div className="logs-empty">No recording files found.</div>
                  ) : (
                    recordings.map((file, idx) => (
                      <button 
                        key={idx}
                        className={`log-item-btn ${selectedRecording === file.name ? 'active' : ''}`}
                        onClick={() => setSelectedRecording(file.name)}
                      >
                        <div className="log-item-title">{file.name}</div>
                        <div className="log-item-meta">
                          {(file.size / 1024).toFixed(1)} KB | {new Date(file.mtime).toLocaleString()}
                        </div>
                      </button>
                    ))
                  )}
                </div>
              </div>

              {/* Recording player body */}
              <div className="log-content-panel">
                <div className="log-content-header">
                  <span>{selectedRecording ? `Playing: ${selectedRecording}` : 'Select a recording to play'}</span>
                </div>
                <div className="log-content-body" style={{ padding: '24px', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
                  {selectedRecording ? (
                    <div className="player-controls-container" style={{ width: '100%', maxWidth: '500px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
                      <div className="audio-player-wrapper" style={{ width: '100%' }}>
                        <audio 
                          key={selectedRecording}
                          src={`${API_BASE}/api/recordings/${selectedRecording}`} 
                          controls 
                          autoPlay 
                          style={{ width: '100%', borderRadius: '8px' }}
                        />
                      </div>
                      <div className="audio-visualizer-placeholder" style={{ display: 'flex', height: '60px', alignItems: 'center', justifyContent: 'center', gap: '6px', background: 'rgba(255,255,255,0.05)', borderRadius: '8px' }}>
                        <div className="visualizer-bar bar-1"></div>
                        <div className="visualizer-bar bar-2"></div>
                        <div className="visualizer-bar bar-3"></div>
                        <div className="visualizer-bar bar-4"></div>
                        <div className="visualizer-bar bar-5"></div>
                        <div className="visualizer-bar bar-6"></div>
                        <div className="visualizer-bar bar-7"></div>
                        <div className="visualizer-bar bar-8"></div>
                        <div className="visualizer-bar bar-9"></div>
                      </div>
                      <div className="player-details" style={{ fontSize: '0.9rem', color: '#888', display: 'flex', flexDirection: 'column', gap: '8px', background: 'rgba(0,0,0,0.2)', padding: '16px', borderRadius: '8px' }}>
                        <p style={{ margin: 0 }}><strong>Format:</strong> 8000 Hz, 16-bit Mono, PCM WAV</p>
                        <p style={{ margin: 0 }}><strong>Codec Decoded:</strong> G.711 A-law (PCMA)</p>
                        <p style={{ margin: 0 }}><strong>Source:</strong> Real-time voice streams captured from SIP E2E test.</p>
                      </div>
                    </div>
                  ) : (
                    <div className="log-content-empty">
                      Click an audio recording file on the left side to load the HTML5 audio player and listen to real-time captured RTP streams.
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {activeTab === 'editor' && (
            <div className="editor-view" style={{ display: 'flex', gap: '20px', height: '100%' }}>
              {/* Left sidebar for file select & presets */}
              <div className="editor-sidebar" style={{ width: '320px', display: 'flex', flexDirection: 'column', gap: '20px', borderRight: '1px solid var(--border-color)', paddingRight: '20px' }}>
                <div>
                  <h3 style={{ fontSize: '12px', textTransform: 'uppercase', color: 'var(--text-secondary)', marginBottom: '10px' }}>File Type</h3>
                  <div className="file-type-toggle" style={{ display: 'flex', gap: '10px' }}>
                    <button
                      className={`btn-action ${editorFileType === 'scenarios' ? 'active' : ''}`}
                      onClick={() => setEditorFileType('scenarios')}
                      style={{ margin: 0, padding: '10px', flex: 1 }}
                    >
                      SIP Scenarios
                    </button>
                    <button
                      className={`btn-action ${editorFileType === 'values' ? 'active' : ''}`}
                      onClick={() => setEditorFileType('values')}
                      style={{ margin: 0, padding: '10px', flex: 1 }}
                    >
                      Test Values
                    </button>
                  </div>
                </div>

                <div>
                  <h3 style={{ fontSize: '12px', textTransform: 'uppercase', color: 'var(--text-secondary)', marginBottom: '10px' }}>Select File</h3>
                  <CustomSelect
                    label=""
                    options={editorFileType === 'scenarios' ? availableScenarios : availableValues}
                    value={editorFileName}
                    onChange={(val) => setEditorFileName(val)}
                    disabled={editorLoading}
                  />
                  <div style={{ marginTop: '10px' }}>
                    <button
                      onClick={() => fileInputRef.current && fileInputRef.current.click()}
                      className="btn-save"
                      style={{ margin: 0, padding: '10px', background: 'var(--accent-green)', color: '#000', width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}
                      disabled={editorLoading}
                    >
                      📁 Import PCAP File
                    </button>
                    <input
                      type="file"
                      ref={fileInputRef}
                      accept=".pcap,.pcapng"
                      style={{ display: 'none' }}
                      onChange={handlePcapUpload}
                    />
                  </div>
                </div>

                {editorFileType === 'scenarios' && (
                  <div className="telecom-presets" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    <h3 style={{ fontSize: '12px', textTransform: 'uppercase', color: 'var(--text-secondary)', marginBottom: '5px' }}>Telecom / OXE Injectors</h3>
                    <p style={{ fontSize: '11px', color: 'var(--text-secondary)', margin: '0 0 10px 0' }}>Place cursor in the INVITE headers block, then click to inject:</p>
                    
                    <button
                      className="btn-save"
                      onClick={() => injectHeader('User-to-User: 04C801C002C4083030303030303030;encoding=hex\r\n')}
                      style={{ margin: 0, textAlign: 'left', padding: '10px', fontSize: '12px' }}
                      title="Inject Alcatel OXE User-to-User Hex payload header"
                    >
                      + Inject User-to-User (UUI)
                    </button>
                    
                    <button
                      className="btn-save"
                      onClick={() => injectHeader('Diversion: <sip:70200@[local_ip]>;reason=unconditional;screen=no;privacy=off\r\n')}
                      style={{ margin: 0, textAlign: 'left', padding: '10px', fontSize: '12px' }}
                      title="Inject Diversion header (used for call forwarding/diversion in PBXs like OXE)"
                    >
                      + Inject Diversion Header
                    </button>

                    <button
                      className="btn-save"
                      onClick={() => injectHeader('P-Asserted-Identity: <sip:[field0]@[local_ip]>\r\n')}
                      style={{ margin: 0, textAlign: 'left', padding: '10px', fontSize: '12px' }}
                      title="Inject P-Asserted-Identity for caller CLI verification"
                    >
                      + Inject P-Asserted-Identity
                    </button>

                    <button
                      className="btn-save"
                      onClick={() => injectHeader('History-Info: <sip:[field1]@[remote_ip]?Reason=SIP%3Bcause%3D302>;index=1\r\nHistory-Info: <sip:[field0]@[local_ip]>;index=1.1\r\n')}
                      style={{ margin: 0, textAlign: 'left', padding: '10px', fontSize: '12px' }}
                      title="Inject History-Info header sequence (standard call routing history)"
                    >
                      + Inject History-Info
                    </button>

                    <button
                      className="btn-save"
                      onClick={() => injectHeader('Privacy: id\r\n')}
                      style={{ margin: 0, textAlign: 'left', padding: '10px', fontSize: '12px' }}
                      title="Inject Privacy: id header (hides caller identity from remote end)"
                    >
                      + Inject Privacy Header
                    </button>

                    {/* Custom Header Injector Form */}
                    <div style={{ marginTop: '15px', padding: '12px', background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border-color)', borderRadius: '6px' }}>
                      <h4 style={{ fontSize: '11px', textTransform: 'uppercase', color: 'var(--text-secondary)', marginBottom: '8px' }}>Custom Injector</h4>
                      <input
                        type="text"
                        placeholder="Header Name (e.g. X-Route)"
                        value={customHeaderName}
                        onChange={(e) => setCustomHeaderName(e.target.value)}
                        style={{ width: '100%', marginBottom: '8px', padding: '6px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: '#fff', fontSize: '11px', borderRadius: '4px' }}
                      />
                      <input
                        type="text"
                        placeholder="Value (e.g. queue1)"
                        value={customHeaderValue}
                        onChange={(e) => setCustomHeaderValue(e.target.value)}
                        style={{ width: '100%', marginBottom: '8px', padding: '6px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: '#fff', fontSize: '11px', borderRadius: '4px' }}
                      />
                      <button
                        className="btn-save"
                        onClick={() => {
                          if (customHeaderName && customHeaderValue) {
                            injectHeader(`${customHeaderName}: ${customHeaderValue}\r\n`);
                            setCustomHeaderName('');
                            setCustomHeaderValue('');
                          }
                        }}
                        style={{ margin: 0, padding: '6px', fontSize: '11px', background: 'var(--accent-cyan)', color: '#000', width: '100%' }}
                        disabled={!customHeaderName || !customHeaderValue}
                      >
                        Inject Custom Header
                      </button>
                    </div>
                  </div>
                )}
              </div>

              {/* Right textarea editor */}
              <div className="editor-main" style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '15px', overflow: 'hidden' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexShrink: 0 }}>
                  <h3 style={{ fontSize: '14px', fontWeight: '600' }}>
                    Editing: <span style={{ color: 'var(--accent-cyan)' }}>{editorFileName || 'None'}</span>
                  </h3>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    {editorFileType === 'scenarios' && (
                      <button
                        onClick={() => setShowPreviewPane(!showPreviewPane)}
                        className="btn-save"
                        style={{ margin: 0, padding: '10px 16px', background: 'rgba(255,255,255,0.05)', color: '#fff' }}
                      >
                        {showPreviewPane ? 'Hide Resolved Preview' : 'Show Resolved Preview'}
                      </button>
                    )}
                    {editorSaveSuccess && <span style={{ color: 'var(--accent-green)', fontSize: '12px' }}>✓ File saved with SIP CRLF endings!</span>}
                    {editorError && <span style={{ color: 'var(--accent-red)', fontSize: '12px' }}>{editorError}</span>}
                    <button
                      className="btn-save"
                      onClick={saveFileContent}
                      disabled={editorLoading || !editorFileName}
                      style={{ margin: 0, padding: '10px 24px', background: 'var(--accent-cyan)', color: '#000', fontWeight: '600' }}
                    >
                      {editorLoading ? 'Saving...' : 'Save File Changes'}
                    </button>
                  </div>
                </div>

                {editorFileType === 'scenarios' && editorContent && (
                  (() => {
                    const warnings = validateScenario(editorContent);
                    if (warnings.length > 0) {
                      return (
                        <div style={{
                          background: 'rgba(234, 179, 8, 0.1)',
                          border: '1px solid #eab308',
                          borderRadius: '8px',
                          padding: '12px 16px',
                          fontSize: '12px',
                          color: '#fef08a',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: '6px',
                          maxHeight: '120px',
                          overflowY: 'auto',
                          flexShrink: 0
                        }}>
                          <strong>⚠️ Scenario Syntax Warnings ({warnings.length}):</strong>
                          <ul style={{ margin: 0, paddingLeft: '20px' }}>
                            {warnings.map((w, idx) => <li key={idx}>{w}</li>)}
                          </ul>
                        </div>
                      );
                    }
                    return null;
                  })()
                )}

                <div className="editor-panels-container" style={{ flex: 1, display: 'flex', gap: '15px', overflow: 'hidden' }}>
                  <div className="editor-container" style={{ flex: 1, position: 'relative', border: '1px solid var(--border-color)', borderRadius: '12px', overflow: 'hidden', background: '#07090e' }}>
                    {editorLoading && (
                      <div style={{ position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 10 }}>
                        Loading content...
                      </div>
                    )}
                    <textarea
                      ref={textareaRef}
                      value={editorContent}
                      onChange={(e) => setEditorContent(e.target.value)}
                      style={{
                        width: '100%',
                        height: '100%',
                        background: 'transparent',
                        border: 'none',
                        color: 'var(--text-primary)',
                        fontFamily: '"Fira Code", monospace',
                        fontSize: '13px',
                        lineHeight: '1.6',
                        padding: '16px',
                        resize: 'none',
                        outline: 'none',
                        overflowY: 'auto',
                      }}
                      placeholder="Select a file to edit or template content will load here."
                      disabled={!editorFileName}
                    />
                  </div>
                  
                  {editorFileType === 'scenarios' && showPreviewPane && (
                    <div className="preview-container" style={{ flex: 1, display: 'flex', flexDirection: 'column', border: '1px solid var(--border-color)', borderRadius: '12px', overflow: 'hidden', background: '#090d16' }}>
                      <div style={{ padding: '12px 16px', background: '#101622', borderBottom: '1px solid var(--border-color)', fontSize: '12px', fontWeight: 'bold', color: 'var(--accent-cyan)' }}>
                        Resolved Placeholders Preview
                      </div>
                      <pre style={{
                        flex: 1,
                        margin: 0,
                        padding: '16px',
                        overflowY: 'auto',
                        fontFamily: '"Fira Code", monospace',
                        fontSize: '12px',
                        lineHeight: '1.6',
                        color: '#94a3b8',
                        whiteSpace: 'pre-wrap'
                      }}>
                        {getResolvedPreview(editorContent, editorMatchingValues, settings)}
                      </pre>
                    </div>
                  )}
                </div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', display: 'flex', justifyContent: 'space-between', flexShrink: 0 }}>
                  <span>⚠️ Every line in SIP templates must end with CRLF (\r\n) for proper PBX parsing. Line endings are normalized to CRLF automatically on save.</span>
                  <span>Character count: {editorContent.length}</span>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'campaigns' && (
            <div className="campaign-view" style={{ display: 'flex', gap: '20px', height: '100%', overflow: 'hidden' }}>
              {/* Left pane: campaigns test case builder list */}
              <div style={{ width: '450px', display: 'flex', flexDirection: 'column', gap: '15px', borderRight: '1px solid var(--border-color)', paddingRight: '20px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <h3 style={{ fontSize: '14px', textTransform: 'uppercase', color: 'var(--text-secondary)', margin: 0 }}>Test Cases Batch</h3>
                  <button 
                    className="btn-save" 
                    onClick={() => setShowAddCampaignModal(true)} 
                    style={{ margin: 0, padding: '6px 12px', background: 'var(--accent-cyan)', color: '#000', fontSize: '12px' }}
                    disabled={campaignRunning}
                  >
                    + Add Test Case
                  </button>
                </div>

                <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '8px', paddingRight: '5px' }}>
                  {campaigns.length === 0 ? (
                    <div className="logs-empty" style={{ paddingTop: '60px' }}>No test cases configured. Add one above to start a campaign.</div>
                  ) : (
                    campaigns.map((item, idx) => {
                      const isExecuting = idx === currentCampaignIndex;
                      return (
                        <div 
                          key={idx}
                          style={{
                            background: isExecuting ? 'rgba(0, 240, 255, 0.03)' : 'rgba(255,255,255,0.01)',
                            border: `1px solid ${isExecuting ? 'var(--accent-cyan)' : 'var(--border-color)'}`,
                            borderRadius: '8px',
                            padding: '12px',
                            position: 'relative',
                            transition: 'all 0.3s'
                          }}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '6px' }}>
                            <div style={{ fontWeight: '600', fontSize: '13px', color: isExecuting ? 'var(--accent-cyan)' : '#fff' }}>
                              {idx + 1}. {item.name}
                            </div>
                            <button
                              onClick={() => {
                                const copy = campaigns.filter((_, cIdx) => cIdx !== idx);
                                saveCampaigns(copy);
                              }}
                              style={{ background: 'none', border: 'none', color: '#ff3131', cursor: 'pointer', fontSize: '10px' }}
                              disabled={campaignRunning}
                            >
                              REMOVE
                            </button>
                          </div>
                          <div style={{ fontSize: '11px', color: 'var(--text-secondary)', display: 'flex', flexDirection: 'column', gap: '3px' }}>
                            <div><strong>Caller Scenario:</strong> {item.callerScenario}</div>
                            <div><strong>Callee Scenario:</strong> {item.calleeScenario}</div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '4px' }}>
                              <span>Expected: <strong style={{ color: item.expectedStatus === 'PASS' ? 'var(--accent-green)' : 'var(--accent-red)' }}>{item.expectedStatus}</strong></span>
                              {item.runStatus && (
                                <span style={{ 
                                  color: item.runStatus === 'Running' ? 'var(--accent-cyan)' : 
                                         (item.runResult === 'PASS' ? 'var(--accent-green)' : 'var(--accent-red)'),
                                  fontWeight: 'bold'
                                }}>
                                  {item.runStatus === 'Running' ? 'RUNNING...' : `RESULT: ${item.runResult}`}
                                  {item.duration && ` (${item.duration}s)`}
                                </span>
                              )}
                            </div>
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>

                <div style={{ borderTop: '1px solid var(--border-color)', paddingTop: '15px' }}>
                  {!campaignRunning ? (
                    <button
                      className="btn-action btn-register"
                      onClick={startCampaignExecution}
                      disabled={campaigns.length === 0}
                      style={{ background: 'var(--accent-cyan)', color: '#000', fontWeight: 'bold' }}
                    >
                      ▶ Execute Campaign Batch
                    </button>
                  ) : (
                    <button
                      className="btn-action btn-stop"
                      onClick={stopCampaignExecution}
                      style={{ background: 'var(--accent-red)', color: '#fff', fontWeight: 'bold' }}
                    >
                      ■ Terminate Campaign Batch
                    </button>
                  )}
                </div>
              </div>

              {/* Right pane: console output log of running campaign / campaign report */}
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '15px', overflow: 'hidden' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexShrink: 0 }}>
                  <h3 style={{ fontSize: '14px', textTransform: 'uppercase', color: 'var(--text-secondary)' }}>Campaign Run Console</h3>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <a
                      href={`${API_BASE}/api/campaigns/export`}
                      className="btn-save"
                      style={{ textDecoration: 'none', margin: 0, padding: '6px 12px', fontSize: '12px', background: 'var(--accent-green)', color: '#000', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}
                      download
                    >
                      📥 Download HTML Report
                    </a>
                    <button
                      onClick={() => window.print()}
                      className="btn-save"
                      style={{ margin: 0, padding: '6px 12px', fontSize: '12px' }}
                      disabled={campaigns.length === 0}
                    >
                      🖨️ Export PDF / Print Report
                    </button>
                    <button
                      onClick={() => setCampaignOutput([])}
                      className="btn-clear-console"
                    >
                      Clear Logs
                    </button>
                  </div>
                </div>

                <div style={{ flex: 1, background: '#05070a', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '16px', overflowY: 'auto', fontFamily: 'Fira Code, monospace', fontSize: '12px', lineHeight: '1.6' }}>
                  {campaignOutput.length === 0 ? (
                    <div className="console-empty" style={{ margin: '150px auto' }}>Console idle. Trigger batch execution to stream stdout/stderr.</div>
                  ) : (
                    campaignOutput.map((line, idx) => {
                      let color = '#f1f3f9';
                      if (line.startsWith('[SYSTEM]')) color = 'var(--accent-cyan)';
                      else if (line.includes('[ERROR]') || line.includes('failed')) color = 'var(--accent-red)';
                      else if (line.includes('PASS')) color = 'var(--accent-green)';
                      else if (line.startsWith('[LOGS] [CALLEE]')) color = '#ffd699';
                      else if (line.startsWith('[LOGS] [CALLER]')) color = '#b5c7ff';

                      return (
                        <div key={idx} style={{ color, marginBottom: '2px', wordBreak: 'break-all' }}>
                          {line}
                        </div>
                      );
                    })
                  )}
                </div>
              </div>

              {/* Add Campaign Modal Dialog */}
              {showAddCampaignModal && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000 }}>
                  <div style={{ background: '#101622', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '24px', width: '450px', display: 'flex', flexDirection: 'column', gap: '15px' }}>
                    <h3 style={{ fontSize: '14px', textTransform: 'uppercase', color: 'var(--accent-cyan)', margin: 0 }}>Add New Test Case</h3>
                    
                    <div className="input-group">
                      <label>Test Case Name</label>
                      <input 
                        type="text" 
                        value={newCampaignItem.name} 
                        onChange={(e) => setNewCampaignItem(prev => ({ ...prev, name: e.target.value }))}
                        style={{ background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', padding: '8px', color: '#fff', borderRadius: '4px' }}
                      />
                    </div>

                    <div className="input-group">
                      <label>Caller Scenario</label>
                      <CustomSelect
                        label=""
                        options={availableScenarios}
                        value={newCampaignItem.callerScenario}
                        onChange={(val) => {
                          const base = val.replace('.txt', '');
                          const matching = `${base}_values.txt`;
                          const exists = availableValues.some(v => v.toLowerCase() === matching.toLowerCase());
                          setNewCampaignItem(prev => ({ 
                            ...prev, 
                            callerScenario: val,
                            callerValues: exists ? availableValues.find(v => v.toLowerCase() === matching.toLowerCase()) : prev.callerValues
                          }));
                        }}
                      />
                    </div>

                    <div className="input-group">
                      <label>Caller Values File</label>
                      <CustomSelect
                        label=""
                        options={availableValues}
                        value={newCampaignItem.callerValues}
                        onChange={(val) => setNewCampaignItem(prev => ({ ...prev, callerValues: val }))}
                      />
                    </div>

                    <div className="input-group">
                      <label>Callee Scenario</label>
                      <CustomSelect
                        label=""
                        options={availableScenarios}
                        value={newCampaignItem.calleeScenario}
                        onChange={(val) => {
                          const base = val.replace('.txt', '');
                          const matching = `${base}_values.txt`;
                          const exists = availableValues.some(v => v.toLowerCase() === matching.toLowerCase());
                          setNewCampaignItem(prev => ({ 
                            ...prev, 
                            calleeScenario: val,
                            calleeValues: exists ? availableValues.find(v => v.toLowerCase() === matching.toLowerCase()) : prev.calleeValues
                          }));
                        }}
                      />
                    </div>

                    <div className="input-group">
                      <label>Callee Values File</label>
                      <CustomSelect
                        label=""
                        options={availableValues}
                        value={newCampaignItem.calleeValues}
                        onChange={(val) => setNewCampaignItem(prev => ({ ...prev, calleeValues: val }))}
                      />
                    </div>

                    <div className="input-group">
                      <label>Expected Result</label>
                      <div style={{ display: 'flex', gap: '15px', marginTop: '5px' }}>
                        <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', cursor: 'pointer' }}>
                          <input 
                            type="radio" 
                            name="expectedStatus" 
                            value="PASS" 
                            checked={newCampaignItem.expectedStatus === 'PASS'}
                            onChange={() => setNewCampaignItem(prev => ({ ...prev, expectedStatus: 'PASS' }))}
                          />
                          PASS (Normal behavior/200 OK)
                        </label>
                        <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', cursor: 'pointer' }}>
                          <input 
                            type="radio" 
                            name="expectedStatus" 
                            value="FAIL" 
                            checked={newCampaignItem.expectedStatus === 'FAIL'}
                            onChange={() => setNewCampaignItem(prev => ({ ...prev, expectedStatus: 'FAIL' }))}
                          />
                          FAIL (Expected rejection code)
                        </label>
                      </div>
                    </div>

                    <div style={{ display: 'flex', gap: '10px', marginTop: '10px' }}>
                      <button
                        className="btn-save"
                        onClick={() => {
                          const updated = [...campaigns, { ...newCampaignItem }];
                          saveCampaigns(updated);
                          setShowAddCampaignModal(false);
                        }}
                        style={{ margin: 0, padding: '10px', background: 'var(--accent-cyan)', color: '#000', fontWeight: 'bold', flex: 1 }}
                      >
                        Add to Batch
                      </button>
                      <button
                        className="btn-action btn-stop"
                        onClick={() => setShowAddCampaignModal(false)}
                        style={{ margin: 0, padding: '10px', flex: 1 }}
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
          {activeTab === 'simulation' && (
            <div className="simulation-view" style={{ display: 'flex', gap: '20px', height: '100%', overflow: 'hidden' }}>
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '20px', maxWidth: '600px' }}>
                <div style={{ background: 'rgba(255,255,255,0.01)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
                  <h3 style={{ fontSize: '14px', textTransform: 'uppercase', color: 'var(--accent-cyan)', margin: 0 }}>Network Degradation Emulation</h3>
                  
                  <div className="input-group">
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                      <label style={{ margin: 0 }}>Latency Delay (ms)</label>
                      <span style={{ fontFamily: 'Fira Code, monospace', color: 'var(--accent-cyan)', fontWeight: 'bold' }}>{simData.delay} ms</span>
                    </div>
                    <input 
                      type="range" 
                      min="0" 
                      max="2000" 
                      step="50"
                      value={simData.delay} 
                      onChange={(e) => saveSimulationSettings({ delay: e.target.value })}
                      style={{ width: '100%', accentColor: 'var(--accent-cyan)' }}
                    />
                  </div>

                  <div className="input-group">
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                      <label style={{ margin: 0 }}>Packet Loss (%)</label>
                      <span style={{ fontFamily: 'Fira Code, monospace', color: 'var(--accent-cyan)', fontWeight: 'bold' }}>{simData.loss} %</span>
                    </div>
                    <input 
                      type="range" 
                      min="0" 
                      max="100" 
                      step="5"
                      value={simData.loss} 
                      onChange={(e) => saveSimulationSettings({ loss: e.target.value })}
                      style={{ width: '100%', accentColor: 'var(--accent-cyan)' }}
                    />
                  </div>
                </div>

                <div style={{ background: 'rgba(255,255,255,0.01)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '24px', display: 'flex', flexDirection: 'column', gap: '15px' }}>
                  <h3 style={{ fontSize: '14px', textTransform: 'uppercase', color: 'var(--accent-cyan)', margin: 0 }}>SIP Protocol Anomaly Injection</h3>
                  
                  <CustomSelect
                    label="Malformed Header"
                    options={["None", "CSeq", "Call-ID", "Via", "Expires"]}
                    value={simData.malformed}
                    onChange={(val) => saveSimulationSettings({ malformed: val })}
                  />
                </div>
              </div>

              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '20px' }}>
                <div style={{ background: 'rgba(255,255,255,0.01)', border: '1px solid var(--accent-cyan)', borderRadius: '12px', padding: '24px', display: 'flex', flexDirection: 'column', gap: '15px', height: '100%' }}>
                  <h3 style={{ fontSize: '14px', textTransform: 'uppercase', color: 'var(--accent-cyan)', margin: 0 }}>SIP State Observability Monitor</h3>
                  
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '15px', marginTop: '10px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px', background: 'rgba(0,0,0,0.2)', borderRadius: '8px' }}>
                      <strong style={{ fontSize: '13px' }}>Registration Status:</strong>
                      <span style={{ 
                        fontWeight: 'bold', 
                        fontSize: '14px',
                        color: simData.regState === 'Registered' ? 'var(--accent-green)' : (simData.regState === 'Registering...' ? 'var(--accent-cyan)' : 'var(--accent-red)') 
                      }}>
                        {simData.regState}
                      </span>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px', background: 'rgba(0,0,0,0.2)', borderRadius: '8px' }}>
                      <strong style={{ fontSize: '13px' }}>Active Call Dialogs:</strong>
                      <span style={{ 
                        fontWeight: 'bold', 
                        fontSize: '14px',
                        color: simData.dialogCount > 0 ? 'var(--accent-green)' : 'var(--text-secondary)'
                      }}>
                        {simData.dialogCount} active
                      </span>
                    </div>
                  </div>
                  
                  <div style={{ fontSize: '11px', color: 'var(--text-secondary)', marginTop: 'auto', background: 'rgba(255,255,255,0.02)', padding: '12px', borderRadius: '6px' }}>
                    💡 <strong>Real-time Observability:</strong> The monitor observes dialog creations and registration lifetimes from the socket engine in real time. Use the network sliders on the left to simulate packet losses and verify automatic SIP UA recovery.
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

export default App;
