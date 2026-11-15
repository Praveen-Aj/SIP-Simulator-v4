const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const os = require('os');
const { spawn, exec } = require('child_process');
const http = require('http');
const xml2js = require('xml2js');

const app = express();
const PORT = process.env.PORT || 3001;

app.use(cors());
app.use(express.json());

// Paths
const TRUNK_DIR = path.resolve(__dirname, '..');
const USER_SETTINGS_PATH = path.join(TRUNK_DIR, 'userSettings.xml');

// Resolve the Java runtime without requiring 'java' on PATH.
// Priority: app-bundled jre -> dev deployment jre -> PATH fallback.
// Quoted because install paths may contain spaces (spawn uses shell:true).
function javaBin() {
    const candidates = [
        path.join(TRUNK_DIR, 'jre', 'bin', 'java.exe'),
        path.join(TRUNK_DIR, 'deployment', 'jre', 'bin', 'java.exe')
    ];
    for (const c of candidates) {
        if (fs.existsSync(c)) return `"${c}"`;
    }
    return 'java';
}

// Classpath that works in both layouts: dev (build\ + lib\) and installed (jar + lib\).
function javaCp() {
    if (fs.existsSync(path.join(TRUNK_DIR, 'build'))) {
        return 'build;lib/*';
    }
    return 'SIP_Simulator_v4.jar;lib/*';
}

// Portable logs directory resolution (checks standard Desktop, OneDrive redirected Desktop, and project-local logs)
function resolveLogsDir(transport = 'UDP') {
    const candidates = [
        path.join(os.homedir(), 'Desktop', 'SIP Simulator logs', transport),
        path.join(os.homedir(), 'OneDrive', 'Desktop', 'SIP Simulator logs', transport),
        path.join(TRUNK_DIR, 'logs', transport)
    ];
    for (const c of candidates) {
        if (fs.existsSync(c)) return c;
    }
    return candidates[0];
}

const LOGS_DIR = resolveLogsDir('UDP');

// Helper to locate a log file across all supported transport log directories
function findLogFilePath(filename) {
    const transports = ['UDP', 'TCP', 'TLS', 'SIP'];
    for (const t of transports) {
        const dir = resolveLogsDir(t);
        const p = path.join(dir, filename);
        if (fs.existsSync(p)) return p;
    }
    for (const t of transports) {
        const p = path.join(TRUNK_DIR, 'logs', t, filename);
        if (fs.existsSync(p)) return p;
    }
    return path.join(LOGS_DIR, filename);
}

// Active child processes tracker - strictly manages processes spawned by this Express server
const activeProcesses = new Set();

function trackChildProcess(proc) {
    if (!proc || !proc.pid) return;
    activeProcesses.add(proc);

    const cleanup = () => {
        activeProcesses.delete(proc);
    };

    proc.once('exit', cleanup);
    proc.once('close', cleanup);
    proc.once('error', cleanup);
}

// Terminate a single child process and its child tree without affecting unrelated Java instances
function terminateChildProcess(proc) {
    return new Promise((resolve) => {
        if (!proc || !proc.pid) {
            return resolve();
        }

        activeProcesses.delete(proc);

        // Check if process has already exited
        if (proc.killed || proc.exitCode !== null) {
            return resolve();
        }

        const pid = proc.pid;

        if (process.platform === 'win32') {
            // On Windows, child_process with shell:true spawns cmd.exe which in turn spawns java.exe.
            // Using "taskkill /T /F /PID <pid>" terminates ONLY that specific PID and its child process tree (/T).
            // It NEVER touches unrelated java.exe processes or the parent Swing desktop application.
            exec(`taskkill /T /F /PID ${pid}`, (err) => {
                // Ignore errors if process already exited
                resolve();
            });
        } else {
            try {
                proc.kill('SIGTERM');
                setTimeout(() => {
                    try {
                        if (!proc.killed) proc.kill('SIGKILL');
                    } catch (e) {}
                    resolve();
                }, 500);
            } catch (e) {
                resolve();
            }
        }
    });
}

// Helper to kill only the child processes spawned by this Express server instance
async function killActiveProcesses() {
    const procs = Array.from(activeProcesses);
    activeProcesses.clear();
    await Promise.all(procs.map(p => terminateChildProcess(p)));
}

// Register graceful process cleanup on Express server exit
function registerShutdownHandlers() {
    const handleShutdown = async (signal) => {
        try {
            await killActiveProcesses();
        } catch (e) {}
        process.exit(0);
    };

    process.once('SIGINT', () => handleShutdown('SIGINT'));
    process.once('SIGTERM', () => handleShutdown('SIGTERM'));

    process.on('exit', () => {
        for (const proc of activeProcesses) {
            if (proc && proc.pid && !proc.killed) {
                try {
                    if (process.platform === 'win32') {
                        require('child_process').execSync(`taskkill /T /F /PID ${proc.pid}`, { stdio: 'ignore' });
                    } else {
                        proc.kill('SIGKILL');
                    }
                } catch (e) {}
            }
        }
    });
}
registerShutdownHandlers();

// Helper to load settings from XML dynamically
async function loadSettings() {
    if (!fs.existsSync(USER_SETTINGS_PATH)) {
        throw new Error('Settings file not found');
    }
    const xmlData = fs.readFileSync(USER_SETTINGS_PATH, 'utf-8');
    const parser = new xml2js.Parser({ explicitArray: false });
    const result = await parser.parseStringPromise(xmlData);
    return result.SIPInspector;
}

// 1. Get status and current settings
app.get('/api/status', async (req, res) => {
    try {
        if (!fs.existsSync(USER_SETTINGS_PATH)) {
            return res.status(404).json({ error: 'Settings file not found' });
        }
        const xmlData = fs.readFileSync(USER_SETTINGS_PATH, 'utf-8');
        const parser = new xml2js.Parser({ explicitArray: false });
        const result = await parser.parseStringPromise(xmlData);
        
        // Add log files list from all supported transport directories
        let logFiles = [];
        const seenFiles = new Set();
        ['UDP', 'TCP', 'TLS', 'SIP'].forEach(t => {
            const dir = resolveLogsDir(t);
            if (fs.existsSync(dir)) {
                fs.readdirSync(dir)
                    .filter(file => file.endsWith('.log'))
                    .forEach(file => {
                        if (!seenFiles.has(file)) {
                            seenFiles.add(file);
                            const stats = fs.statSync(path.join(dir, file));
                            logFiles.push({
                                name: file,
                                transport: t,
                                size: stats.size,
                                mtime: stats.mtime
                            });
                        }
                    });
            }
        });
        logFiles.sort((a, b) => b.mtime - a.mtime);

        res.json({
            settings: result.SIPInspector,
            logFiles
        });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 2. Save settings to XML
app.post('/api/settings', async (req, res) => {
    try {
        const { localIPAddress, localPort, remoteIPAddress, remotePort, username } = req.body;
        
        if (!fs.existsSync(USER_SETTINGS_PATH)) {
            return res.status(404).json({ error: 'Settings file not found' });
        }
        
        const xmlData = fs.readFileSync(USER_SETTINGS_PATH, 'utf-8');
        const parser = new xml2js.Parser({ explicitArray: false });
        const result = await parser.parseStringPromise(xmlData);
        
        // Update fields
        if (result.SIPInspector.serversInfo) {
            result.SIPInspector.serversInfo.localIPAddress = localIPAddress || result.SIPInspector.serversInfo.localIPAddress;
            result.SIPInspector.serversInfo.localPort = localPort || result.SIPInspector.serversInfo.localPort;
            result.SIPInspector.serversInfo.remoteIPAddress = remoteIPAddress || result.SIPInspector.serversInfo.remoteIPAddress;
            result.SIPInspector.serversInfo.remotePort = remotePort || result.SIPInspector.serversInfo.remotePort;
        }
        if (result.SIPInspector.account) {
            result.SIPInspector.account.username = username || result.SIPInspector.account.username;
        }

        const builder = new xml2js.Builder();
        const updatedXml = builder.buildObject(result);
        fs.writeFileSync(USER_SETTINGS_PATH, updatedXml, 'utf-8');

        res.json({ success: true, message: 'Settings saved successfully' });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 3. Stop running tests
app.post('/api/test/stop', async (req, res) => {
    await killActiveProcesses();
    res.json({ success: true, message: 'All active tests stopped and processes killed' });
});

// 4. Stream Registration Test Output (SSE)
app.get('/api/stream/register', async (req, res) => {
    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');
    res.flushHeaders();

    res.write(`data: [SYSTEM] Starting SIP Registration test...\n\n`);
    await killActiveProcesses();

    const command = javaBin();
    const args = ['-cp', javaCp(), 'sipinspector.Media.CliSipRegisterTest'];

    const child = spawn(command, args, { cwd: TRUNK_DIR, shell: true });
    trackChildProcess(child);

    child.stdout.on('data', (data) => {
        const lines = data.toString().split('\n');
        lines.forEach(line => {
            if (line.trim()) {
                trackSipStateFromConsole(line);
                res.write(`data: ${line}\n\n`);
            }
        });
    });

    child.stderr.on('data', (data) => {
        const lines = data.toString().split('\n');
        lines.forEach(line => {
            if (line.trim()) {
                res.write(`data: [ERROR] ${line}\n\n`);
            }
        });
    });

    child.on('close', (code) => {
        res.write(`data: [SYSTEM] Test finished with exit code ${code}\n\n`);
        res.end();
    });

    req.on('close', () => {
        terminateChildProcess(child);
    });
});

// 5. Stream Call + Media Test Output (SSE)
app.get('/api/stream/call', async (req, res) => {
    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');
    res.flushHeaders();

    try {
        const { callerScenario, callerValues, calleeScenario, calleeValues } = req.query;

        const settings = await loadSettings();
        const calleeUser = settings.account?.username || '70201';
        let callerUser = '70200';
        if (!isNaN(calleeUser)) {
            callerUser = String(parseInt(calleeUser) - 1);
        } else {
            callerUser = calleeUser + '_caller';
        }
        
        const calleePort = settings.serversInfo?.localPort || '5060';
        let callerPort = '5070';
        if (!isNaN(calleePort)) {
            callerPort = String(parseInt(calleePort) + 10);
        }

        res.write(`data: [SYSTEM] Spawning two-way call instances...\n\n`);
        await killActiveProcesses();

        // 1. Spawn callee in background
        res.write(`data: [SYSTEM] Launching Callee instance on port ${calleePort} for user ${calleeUser}...\n\n`);
        const calleeArgs = [];
        if (calleeScenario) {
            calleeArgs.push(`-DscenarioPath=scenarios/${calleeScenario}`);
        }
        if (calleeValues) {
            calleeArgs.push(`-DvaluesPath=values/${calleeValues}`);
        }
        calleeArgs.push('-Dlogs.unique.id=callee', '-cp', javaCp(), 'sipinspector.Media.CliSipLegTest', 'callee', calleePort, calleeUser);
        const calleeProcess = spawn(javaBin(), calleeArgs, { cwd: TRUNK_DIR, shell: true });
        trackChildProcess(calleeProcess);

        calleeProcess.stdout.on('data', (data) => {
            data.toString().split('\n').forEach(line => {
                if (line.trim()) {
                    trackSipStateFromConsole(line);
                    res.write(`data: [CALLEE] ${line}\n\n`);
                }
            });
        });

        // 2. Wait 3 seconds, then spawn caller
        setTimeout(() => {
            res.write(`data: [SYSTEM] Launching Caller instance on port ${callerPort} for user ${callerUser} calling ${calleeUser}...\n\n`);
            const callerArgs = [];
            if (callerScenario) {
                callerArgs.push(`-DscenarioPath=scenarios/${callerScenario}`);
            }
            if (callerValues) {
                callerArgs.push(`-DvaluesPath=values/${callerValues}`);
            }
            callerArgs.push('-Dlogs.unique.id=caller', '-cp', javaCp(), 'sipinspector.Media.CliSipLegTest', 'caller', callerPort, callerUser, calleeUser);
            const callerProcess = spawn(javaBin(), callerArgs, { cwd: TRUNK_DIR, shell: true });
            trackChildProcess(callerProcess);

            callerProcess.stdout.on('data', (data) => {
                data.toString().split('\n').forEach(line => {
                    if (line.trim()) {
                        trackSipStateFromConsole(line);
                        res.write(`data: [CALLER] ${line}\n\n`);
                    }
                });
            });

            callerProcess.stderr.on('data', (data) => {
                data.toString().split('\n').forEach(line => {
                    if (line.trim()) res.write(`data: [CALLER-ERROR] ${line}\n\n`);
                });
            });

            callerProcess.on('close', (code) => {
                res.write(`data: [SYSTEM] Caller completed with code ${code}. Stopping Callee...\n\n`);
                terminateChildProcess(calleeProcess);
                res.write(`data: [SYSTEM] End-to-end call test completed.\n\n`);
                res.end();
            });
        }, 3000);

    } catch (e) {
        res.write(`data: [ERROR] Failed to load settings: ${e.message}\n\n`);
        res.end();
    }

    req.on('close', () => {
        killActiveProcesses();
    });
});

// 5.5. Get available scenarios and values
app.get('/api/scenarios-values', (req, res) => {
    try {
        const scenariosDir = path.join(TRUNK_DIR, 'scenarios');
        const valuesDir = path.join(TRUNK_DIR, 'values');
        
        let scenarios = [];
        if (fs.existsSync(scenariosDir)) {
            scenarios = fs.readdirSync(scenariosDir).filter(f => f.endsWith('.txt'));
        }
        
        let values = [];
        if (fs.existsSync(valuesDir)) {
            values = fs.readdirSync(valuesDir).filter(f => f.endsWith('.txt'));
        }
        
        res.json({ scenarios, values });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 5.8. Get file content (scenarios or values)
app.get('/api/files/:type/:filename', (req, res) => {
    const { type, filename } = req.params;
    if (type !== 'scenarios' && type !== 'values') {
        return res.status(400).json({ error: 'Invalid file type' });
    }
    if (filename.includes('..') || filename.includes('/') || filename.includes('\\')) {
        return res.status(403).json({ error: 'Access denied' });
    }
    const filePath = path.join(TRUNK_DIR, type, filename);
    if (!fs.existsSync(filePath)) {
        return res.status(404).json({ error: 'File not found' });
    }
    const content = fs.readFileSync(filePath, 'utf-8');
    res.json({ content });
});

// 5.9. Save file content (scenarios or values) with SIP CRLF endings
app.put('/api/files/:type/:filename', (req, res) => {
    const { type, filename } = req.params;
    const { content } = req.body;
    if (type !== 'scenarios' && type !== 'values') {
        return res.status(400).json({ error: 'Invalid file type' });
    }
    if (filename.includes('..') || filename.includes('/') || filename.includes('\\')) {
        return res.status(403).json({ error: 'Access denied' });
    }
    const filePath = path.join(TRUNK_DIR, type, filename);
    
    // Ensure all line endings are CRLF (\r\n) for SIP protocol compliance
    let normalizedContent = content.replace(/\r\n/g, '\n').replace(/\n/g, '\r\n');
    
    try {
        fs.writeFileSync(filePath, normalizedContent, 'utf-8');
        res.json({ success: true, message: `${filename} saved successfully with SIP CRLF endings.` });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 6. Get log content
app.get('/api/logs/:filename', (req, res) => {
    const filename = req.params.filename;
    // Safety check path traversal
    if (filename.includes('..') || filename.includes('/') || filename.includes('\\')) {
        return res.status(403).json({ error: 'Access denied' });
    }
    const filePath = findLogFilePath(filename);
    if (!fs.existsSync(filePath)) {
        return res.status(404).json({ error: 'Log file not found' });
    }
    const content = fs.readFileSync(filePath, 'utf-8');
    res.json({ content });
});

// 6.5. Get SIP log ladder diagram structured data
app.get('/api/logs/:filename/ladder', (req, res) => {
    const filename = req.params.filename;
    if (filename.includes('..') || filename.includes('/') || filename.includes('\\')) {
        return res.status(403).json({ error: 'Access denied' });
    }
    const filePath = findLogFilePath(filename);
    if (!fs.existsSync(filePath)) {
        return res.status(404).json({ error: 'Log file not found' });
    }
    try {
        const content = fs.readFileSync(filePath, 'utf-8');
        const lines = content.split(/\r?\n/);
        const messages = [];
        let currentMsg = null;
        let parsingHeader = false;
        let rawTextLines = [];
        let lastMetadataLine = '';

        for (let i = 0; i < lines.length; i++) {
            const line = lines[i].trim();
            
            if (line.startsWith('From:') && line.includes(' - To:')) {
                lastMetadataLine = line;
                continue;
            }

            if (line === '---------->' || line === '<----------') {
                if (currentMsg) {
                    currentMsg.rawText = rawTextLines.join('\n');
                    messages.push(currentMsg);
                }
                
                let source = 'Local';
                let target = 'Remote';
                if (lastMetadataLine) {
                    const match = lastMetadataLine.match(/From:([^\s]+)\s+-\s+To:([^\s]+)/);
                    if (match) {
                        source = match[1];
                        target = match[2];
                    }
                } else {
                    if (line === '---------->') {
                        source = 'Local';
                        target = 'Remote';
                    } else {
                        source = 'Remote';
                        target = 'Local';
                    }
                }

                currentMsg = {
                    source,
                    target,
                    direction: line === '---------->' ? 'out' : 'in',
                    label: '',
                    callId: '',
                    cseq: '',
                    rawText: ''
                };
                rawTextLines = [];
                parsingHeader = true;
                continue;
            }

            if (line === '----------' || line.startsWith('cmd_') || line.startsWith('//')) {
                if (currentMsg) {
                    currentMsg.rawText = rawTextLines.join('\n');
                    messages.push(currentMsg);
                    currentMsg = null;
                }
                parsingHeader = false;
                lastMetadataLine = '';
                continue;
            }

            if (currentMsg) {
                rawTextLines.push(lines[i]);

                if (parsingHeader && line !== '') {
                    if (!currentMsg.label) {
                        if (line.startsWith('SIP/2.0')) {
                            currentMsg.label = line.substring(8);
                        } else if (line.includes(' SIP/2.0')) {
                            const parts = line.split(' ');
                            currentMsg.label = parts[0];
                        } else {
                            currentMsg.label = line;
                        }
                    }

                    if (line.toLowerCase().startsWith('call-id:')) {
                        currentMsg.callId = line.substring(8).trim();
                    }

                    if (line.toLowerCase().startsWith('cseq:')) {
                        currentMsg.cseq = line.substring(5).trim();
                    }
                } else if (line === '') {
                    parsingHeader = false;
                }
            }
        }

        if (currentMsg) {
            currentMsg.rawText = rawTextLines.join('\n');
            messages.push(currentMsg);
        }

        res.json({ messages });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 6.6. Campaigns configuration file path and routes
const CAMPAIGNS_FILE_PATH = path.join(TRUNK_DIR, 'campaigns.json');

app.get('/api/campaigns', (req, res) => {
    try {
        if (!fs.existsSync(CAMPAIGNS_FILE_PATH)) {
            return res.json([]);
        }
        const data = fs.readFileSync(CAMPAIGNS_FILE_PATH, 'utf-8');
        res.json(JSON.parse(data));
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

app.put('/api/campaigns', (req, res) => {
    try {
        const campaigns = req.body;
        fs.writeFileSync(CAMPAIGNS_FILE_PATH, JSON.stringify(campaigns, null, 2), 'utf-8');
        res.json({ success: true, message: 'Campaigns saved successfully.' });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 6.7. Simulation settings properties helper and endpoints
const SETTINGS_PROPERTIES_PATH = path.join(TRUNK_DIR, 'config', 'settings.properties');

function loadProperties() {
    const props = {};
    if (fs.existsSync(SETTINGS_PROPERTIES_PATH)) {
        const content = fs.readFileSync(SETTINGS_PROPERTIES_PATH, 'utf-8');
        content.split(/\r?\n/).forEach(line => {
            const trimmed = line.trim();
            if (trimmed && !trimmed.startsWith('#') && trimmed.includes('=')) {
                const parts = trimmed.split('=');
                const key = parts[0].trim();
                const val = parts.slice(1).join('=').trim();
                props[key] = val;
            }
        });
    }
    return props;
}

function saveProperties(propsToUpdate) {
    const existingProps = loadProperties();
    const merged = { ...existingProps, ...propsToUpdate };
    let content = '# SIP Simulator Settings\r\n';
    Object.keys(merged).forEach(key => {
        content += `${key}=${merged[key]}\r\n`;
    });
    const configDir = path.dirname(SETTINGS_PROPERTIES_PATH);
    if (!fs.existsSync(configDir)) {
        fs.mkdirSync(configDir, { recursive: true });
    }
    fs.writeFileSync(SETTINGS_PROPERTIES_PATH, content, 'utf-8');
}

let webRegState = 'Unregistered';
let webDialogCount = 0;
let webActiveDialogs = new Set();

function trackSipStateFromConsole(line) {
    if (!line) return;
    try {
        if (line.includes('REGISTER sip:')) {
            webRegState = 'Registering...';
        } else if (line.includes('SIP/2.0 200 OK') && line.includes('REGISTER')) {
            webRegState = 'Registered';
        } else if (line.includes('SIP/2.0 401') || line.includes('SIP/2.0 403')) {
            webRegState = 'Unregistered';
        }

        if (line.includes('INVITE sip:')) {
            const callIdMatch = line.match(/Call-ID:\s*([^\r\n\s;]+)/i);
            if (callIdMatch) {
                webActiveDialogs.add(callIdMatch[1]);
            } else {
                webActiveDialogs.add(Math.random().toString());
            }
            webDialogCount = webActiveDialogs.size;
        } else if (line.includes('BYE sip:') || line.includes('SIP/2.0 488') || line.includes('SIP/2.0 500')) {
            const callIdMatch = line.match(/Call-ID:\s*([^\r\n\s;]+)/i);
            if (callIdMatch) {
                webActiveDialogs.delete(callIdMatch[1]);
            } else if (webActiveDialogs.size > 0) {
                const first = webActiveDialogs.values().next().value;
                webActiveDialogs.delete(first);
            }
            webDialogCount = webActiveDialogs.size;
        }
    } catch (e) {
        console.error("Error tracking SIP state: ", e);
    }
}

app.get('/api/simulation', (req, res) => {
    try {
        const props = loadProperties();
        res.json({
            delay: props['sim.delay'] || '0',
            loss: props['sim.loss'] || '0',
            malformed: props['sim.malformed'] || 'None',
            regState: webRegState,
            dialogCount: webDialogCount
        });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

app.put('/api/simulation', (req, res) => {
    try {
        const { delay, loss, malformed } = req.body;
        const updates = {};
        if (delay !== undefined) updates['sim.delay'] = String(delay);
        if (loss !== undefined) updates['sim.loss'] = String(loss);
        if (malformed !== undefined) updates['sim.malformed'] = String(malformed);
        saveProperties(updates);
        res.json({ success: true, message: 'Simulation settings updated successfully.' });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 7. Get all audio recordings
app.get('/api/recordings', (req, res) => {
    const recordingsDir = path.join(TRUNK_DIR, 'recordings');
    try {
        if (!fs.existsSync(recordingsDir)) {
            return res.json([]);
        }
        const files = fs.readdirSync(recordingsDir)
            .filter(file => file.endsWith('.wav'))
            .map(file => {
                const stats = fs.statSync(path.join(recordingsDir, file));
                return {
                    name: file,
                    size: stats.size,
                    mtime: stats.mtime
                };
            })
            .sort((a, b) => b.mtime - a.mtime);
        res.json(files);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 8. Stream/Serve a specific recording file
app.get('/api/recordings/:filename', (req, res) => {
    const filename = req.params.filename;
    if (filename.includes('..') || filename.includes('/') || filename.includes('\\')) {
        return res.status(403).json({ error: 'Access denied' });
    }
    const recordingsDir = path.join(TRUNK_DIR, 'recordings');
    const filePath = path.join(recordingsDir, filename);
    if (!fs.existsSync(filePath)) {
        return res.status(404).json({ error: 'Recording file not found' });
    }
    res.sendFile(filePath);
});
// GET export campaign reports
function parseLogToMessages(filePath) {
    if (!fs.existsSync(filePath)) return [];
    try {
        let localIp = '';
        let remoteIp = '';
        try {
            const props = loadProperties();
            localIp = props['local_ip'] || '';
            remoteIp = props['remote_ip'] || '';
        } catch (e) {}

        function normalizeHost(host) {
            if (!host) return 'SIP Simulator(LOCAL)';
            const clean = host.trim();
            const cleanLower = clean.toLowerCase();

            let isLocal = false;
            if (cleanLower === 'local' || cleanLower.includes('127.0.0.1') || cleanLower.includes('localhost') || cleanLower.includes('inspector') || cleanLower.includes('simulator')) {
                isLocal = true;
            } else if (localIp && clean.includes(localIp)) {
                isLocal = true;
            }

            if (isLocal) {
                return 'SIP Simulator(LOCAL)';
            }

            if (remoteIp) {
                return remoteIp + '(Remote)';
            }

            let parsedHost = clean;
            if (parsedHost.startsWith('rc/')) {
                parsedHost = parsedHost.substring(3);
            }
            if (parsedHost.startsWith('rc /')) {
                parsedHost = parsedHost.substring(4);
            }
            if (parsedHost.includes(':')) {
                parsedHost = parsedHost.split(':')[0];
            }
            parsedHost = parsedHost.trim();
            if (!parsedHost || parsedHost.toLowerCase() === 'remote' || parsedHost.toLowerCase() === 'server') {
                return '192.168.0.70(Remote)';
            }
            return parsedHost + '(Remote)';
        }

        const content = fs.readFileSync(filePath, 'utf-8');
        const lines = content.split(/\r?\n/);
        const messages = [];
        let currentMsg = null;
        let parsingHeader = false;
        let insideMessageBody = false;
        let rawTextLines = [];
        let lastMetadataLine = '';
        
        let debugDirection = '';
        let debugRemote = '';

        for (let i = 0; i < lines.length; i++) {
            let line = lines[i].trim();
            let originalLine = lines[i];

            // Strip timestamp prefix if present (e.g. "2026-06-21 08:54:48.718 ")
            if (line.length >= 23 && line.substring(0, 10).includes('-') && line.substring(10, 19).includes(':')) {
                const possibleTs = line.substring(0, 23);
                if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}$/.test(possibleTs)) {
                    line = line.substring(23).trim();
                    originalLine = originalLine.substring(originalLine.indexOf(possibleTs) + 23);
                    if (originalLine.startsWith(' ')) {
                        originalLine = originalLine.substring(1);
                    }
                }
            }

            // Check Format B (Debug log Send/Receive markers)
            if (line.includes('SEND DEBUG') || line.includes('RECEIVE DEBUG') || line.includes('RECV DEBUG')) {
                debugDirection = line.includes('SEND DEBUG') ? 'out' : 'in';
                debugRemote = '';
                continue;
            }

            if ((line.startsWith('To:') || line.startsWith('From:')) && debugDirection && !parsingHeader && !insideMessageBody) {
                let addr = line.substring(3).trim();
                if (addr.startsWith('/')) {
                    addr = addr.substring(1);
                }
                debugRemote = addr;
                continue;
            }

            if (line === '--- SIP MESSAGE START ---') {
                if (currentMsg) {
                    currentMsg.rawText = rawTextLines.join('\n');
                    messages.push(currentMsg);
                }
                
                let source = 'Local';
                let target = 'Remote';
                if (debugDirection === 'out') {
                    source = 'Local';
                    target = debugRemote || 'Remote';
                } else {
                    source = debugRemote || 'Remote';
                    target = 'Local';
                }

                currentMsg = {
                    source: normalizeHost(source),
                    target: normalizeHost(target),
                    direction: debugDirection,
                    label: '',
                    callId: '',
                    cseq: '',
                    rawText: ''
                };
                rawTextLines = [];
                parsingHeader = true;
                insideMessageBody = true;
                continue;
            }

            if (line === '--- SIP MESSAGE END ---') {
                if (currentMsg) {
                    currentMsg.rawText = rawTextLines.join('\n');
                    messages.push(currentMsg);
                    currentMsg = null;
                }
                parsingHeader = false;
                insideMessageBody = false;
                debugDirection = '';
                debugRemote = '';
                continue;
            }

            // Check Format A (Standard clean SIP logs metadata lines)
            if ((line.startsWith('From:') || line.startsWith('From')) && line.includes(' - To:') && !insideMessageBody) {
                lastMetadataLine = line;
                continue;
            }

            if ((line === '---------->' || line === '<----------' || line === 'u->' || line === 'u<-') && !insideMessageBody) {
                if (currentMsg) {
                    currentMsg.rawText = rawTextLines.join('\n');
                    messages.push(currentMsg);
                }
                
                let source = 'Local';
                let target = 'Remote';
                if (lastMetadataLine) {
                    const match = lastMetadataLine.match(/From:?([^\s]+)\s+-\s+To:([^\s]+)/);
                    if (match) {
                        source = match[1];
                        target = match[2];
                    }
                } else {
                    if (line === '---------->' || line === 'u->') {
                        source = 'Local';
                        target = 'Remote';
                    } else {
                        source = 'Remote';
                        target = 'Local';
                    }
                }

                currentMsg = {
                    source: normalizeHost(source),
                    target: normalizeHost(target),
                    direction: (line === '---------->' || line === 'u->') ? 'out' : 'in',
                    label: '',
                    callId: '',
                    cseq: '',
                    rawText: ''
                };
                rawTextLines = [];
                parsingHeader = true;
                continue;
            }

            if (line === '----------' || line.startsWith('cmd_') || line.startsWith('//')) {
                if (currentMsg) {
                    currentMsg.rawText = rawTextLines.join('\n');
                    messages.push(currentMsg);
                    currentMsg = null;
                }
                parsingHeader = false;
                lastMetadataLine = '';
                continue;
            }

            if (currentMsg) {
                rawTextLines.push(originalLine);

                if (parsingHeader && line !== '') {
                    if (!currentMsg.label) {
                        if (line.startsWith('SIP/2.0')) {
                            currentMsg.label = line.substring(8);
                        } else if (line.includes(' SIP/2.0')) {
                            const parts = line.split(' ');
                            currentMsg.label = parts[0];
                        } else {
                            currentMsg.label = line;
                        }
                    }

                    if (line.toLowerCase().startsWith('call-id:')) {
                        currentMsg.callId = line.substring(8).trim();
                    }

                    if (line.toLowerCase().startsWith('cseq:')) {
                        currentMsg.cseq = line.substring(5).trim();
                    }
                } else if (line === '') {
                    parsingHeader = false;
                }
            }
        }

        if (currentMsg) {
            currentMsg.rawText = rawTextLines.join('\n');
            messages.push(currentMsg);
        }

        function cleanHostString(host) {
            if (!host) return '';
            let clean = host.trim();
            if (clean.startsWith('/')) {
                clean = clean.substring(1);
            }
            if (clean.startsWith('rc/')) {
                clean = clean.substring(3);
            }
            if (clean.startsWith('rc /')) {
                clean = clean.substring(4);
            }
            if (clean.includes(':')) {
                clean = clean.split(':')[0];
            }
            return clean.trim();
        }

        // Post-process to normalize source and target to exactly two columns
        let localHost = localIp;
        let remoteHost = remoteIp;

        for (let i = 0; i < messages.length; i++) {
            let msg = messages[i];
            let srcClean = cleanHostString(msg.source);
            let tgtClean = cleanHostString(msg.target);
            
            if (msg.direction === 'out') {
                if (!localHost) {
                    if (srcClean.toLowerCase() !== 'local' && srcClean.toLowerCase() !== 'remote' && srcClean && !srcClean.includes('LOCAL') && !srcClean.includes('Remote')) {
                        localHost = srcClean;
                    }
                }
                if (!remoteHost) {
                    if (tgtClean.toLowerCase() !== 'local' && tgtClean.toLowerCase() !== 'remote' && tgtClean && !tgtClean.includes('LOCAL') && !tgtClean.includes('Remote')) {
                        remoteHost = tgtClean;
                    }
                }
            } else if (msg.direction === 'in') {
                if (!remoteHost) {
                    if (srcClean.toLowerCase() !== 'local' && srcClean.toLowerCase() !== 'remote' && srcClean && !srcClean.includes('LOCAL') && !srcClean.includes('Remote')) {
                        remoteHost = srcClean;
                    }
                }
                if (!localHost) {
                    if (tgtClean.toLowerCase() !== 'local' && tgtClean.toLowerCase() !== 'remote' && tgtClean && !tgtClean.includes('LOCAL') && !tgtClean.includes('Remote')) {
                        localHost = tgtClean;
                    }
                }
            }
        }

        if (!localHost) {
            localHost = 'SIP Simulator';
        }
        if (!remoteHost) {
            remoteHost = 'Remote';
        }

        for (let i = 0; i < messages.length; i++) {
            let msg = messages[i];
            let srcClean = cleanHostString(msg.source);
            let tgtClean = cleanHostString(msg.target);

            let srcIsLocal = false;
            if (srcClean.toLowerCase() === 'local' || srcClean.toLowerCase() === 'sip simulator' || srcClean.toLowerCase() === 'sip inspector' || srcClean.includes('LOCAL')) {
                srcIsLocal = true;
            } else if (localHost && srcClean.toLowerCase() === localHost.toLowerCase()) {
                srcIsLocal = true;
            } else if (msg.direction === 'out' && (srcClean.toLowerCase() === 'local' || !srcClean)) {
                srcIsLocal = true;
            }

            let tgtIsLocal = false;
            if (tgtClean.toLowerCase() === 'local' || tgtClean.toLowerCase() === 'sip simulator' || tgtClean.toLowerCase() === 'sip inspector' || tgtClean.includes('LOCAL')) {
                tgtIsLocal = true;
            } else if (localHost && tgtClean.toLowerCase() === localHost.toLowerCase()) {
                tgtIsLocal = true;
            } else if (msg.direction === 'in' && (tgtClean.toLowerCase() === 'local' || !tgtClean)) {
                tgtIsLocal = true;
            }

            msg.source = srcIsLocal ? 'SIP Simulator(LOCAL)' : remoteHost + '(Remote)';
            msg.target = tgtIsLocal ? 'SIP Simulator(LOCAL)' : remoteHost + "(Remote)";
        }

        return messages;
    } catch (err) {
        return [];
    }
}

function generateSvgLadder(messages) {
    if (!messages || messages.length === 0) return '';
    
    const itemHeight = 45;
    const padding = 40;
    const width = 650;
    const height = (messages.length + 1) * itemHeight + padding * 2;
    
    let svg = `<svg width="100%" height="${height}" viewBox="0 0 ${width} ${height}" xmlns="http://www.w3.org/2000/svg" style="background:#0f172a; border-radius:12px; border:1px solid #334155; margin-top:16px; font-family:'Inter', sans-serif; box-shadow: inset 0 2px 4px 0 rgba(0, 0, 0, 0.06);">`;
    
    const x1 = 180;
    const x2 = 470;
    
    // Vertical timelines
    svg += `<line x1="${x1}" y1="${padding}" x2="${x1}" y2="${height - padding}" stroke="#475569" stroke-width="2" stroke-dasharray="4 4"/>`;
    svg += `<line x1="${x2}" y1="${padding}" x2="${x2}" y2="${height - padding}" stroke="#475569" stroke-width="2" stroke-dasharray="4 4"/>`;
    
    // Node headers
    svg += `<circle cx="${x1}" cy="${padding}" r="6" fill="#06b6d4"/>`;
    svg += `<circle cx="${x2}" cy="${padding}" r="6" fill="#10b981"/>`;
    svg += `<text x="${x1}" y="${padding - 12}" fill="#38bdf8" font-size="12" font-weight="bold" text-anchor="middle">Local IP</text>`;
    svg += `<text x="${x2}" y="${padding - 12}" fill="#34d399" font-size="12" font-weight="bold" text-anchor="middle">Remote PBX</text>`;
    
    messages.forEach((msg, idx) => {
        const y = padding + (idx + 1) * itemHeight;
        const isOut = msg.direction === 'out';
        
        const fromX = isOut ? x1 : x2;
        const toX = isOut ? x2 : x1;
        const color = isOut ? '#06b6d4' : '#10b981'; // Cyan for Out, Green for In
        
        // Message line
        svg += `<line x1="${fromX}" y1="${y}" x2="${toX}" y2="${y}" stroke="${color}" stroke-width="2"/>`;
        
        // Arrow head polygon
        const arrowSize = 6;
        if (isOut) {
            svg += `<polygon points="${toX},${y} ${toX-arrowSize},${y-arrowSize} ${toX-arrowSize},${y+arrowSize}" fill="${color}"/>`;
        } else {
            svg += `<polygon points="${toX},${y} ${toX+arrowSize},${y-arrowSize} ${toX+arrowSize},${y+arrowSize}" fill="${color}"/>`;
        }
        
        // Label text
        svg += `<rect x="${(x1+x2)/2 - 100}" y="${y - 18}" width="200" height="15" fill="#0f172a" rx="3"/>`;
        svg += `<text x="${(x1 + x2) / 2}" y="${y - 6}" fill="#f1f5f9" font-size="11" font-weight="500" text-anchor="middle">${msg.label} ${msg.cseq ? '(' + msg.cseq.split(' ')[0] + ')' : ''}</text>`;
    });
    
    // Bottom circles
    svg += `<circle cx="${x1}" cy="${height - padding}" r="6" fill="#06b6d4"/>`;
    svg += `<circle cx="${x2}" cy="${height - padding}" r="6" fill="#10b981"/>`;
    
    svg += `</svg>`;
    return svg;
}

// GET export campaign reports
app.get('/api/campaigns/export', (req, res) => {
    try {
        let campaignsData = [];
        if (fs.existsSync(CAMPAIGNS_FILE_PATH)) {
            campaignsData = JSON.parse(fs.readFileSync(CAMPAIGNS_FILE_PATH, 'utf-8'));
        }
        
        // Find and sort caller log files chronologically across desktop and project logs
        const logFiles = [];
        const seenLogPaths = new Set();
        const transports = ['UDP', 'TCP', 'TLS', 'SIP'];
        transports.forEach(t => {
            const candidateDirs = [resolveLogsDir(t), path.join(TRUNK_DIR, 'logs', t)];
            candidateDirs.forEach(dir => {
                if (fs.existsSync(dir)) {
                    fs.readdirSync(dir).forEach(f => {
                        if (f.endsWith('.log') && f.includes('_caller')) {
                            const filePath = path.join(dir, f);
                            if (!seenLogPaths.has(filePath)) {
                                seenLogPaths.add(filePath);
                                const stat = fs.statSync(filePath);
                                logFiles.push({ path: filePath, mtime: stat.mtimeMs });
                            }
                        }
                    });
                }
            });
        });
        
        logFiles.sort((a, b) => a.mtime - b.mtime);
        
        // Map logs from the end
        const startIndex = Math.max(0, logFiles.length - campaignsData.length);
        const mappedLogs = logFiles.slice(startIndex);
        
        let reportHtml = `<!DOCTYPE html>
<html>
<head>
    <title>SIP Simulator Campaign Report</title>
    <style>
        body { font-family: 'Inter', -apple-system, sans-serif; background-color: #0b0f19; color: #f1f5f9; padding: 40px; margin: 0; }
        .container { max-width: 900px; margin: 0 auto; background: #111827; border: 1px solid #1f2937; border-radius: 12px; padding: 32px; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.3); }
        h1 { color: #00f0ff; margin-top: 0; border-bottom: 2px solid #1f2937; padding-bottom: 12px; font-size: 24px; }
        .meta { color: #9ca3af; font-size: 14px; margin-bottom: 24px; }
        .tc-card { background: #1f2937; border: 1px solid #374151; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
        .tc-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #374151; padding-bottom: 8px; margin-bottom: 8px; }
        .tc-title { font-weight: bold; font-size: 16px; color: #fff; }
        .tc-badge { padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }
        .tc-badge.pass { background: rgba(74, 222, 128, 0.1); color: #4ade80; border: 1px solid #4ade80; }
        .tc-badge.fail { background: rgba(239, 68, 68, 0.1); color: #ef4444; border: 1px solid #ef4444; }
        .tc-badge.pending { background: rgba(156, 163, 175, 0.1); color: #9ca3af; border: 1px solid #9ca3af; }
        .tc-details { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; font-size: 13px; color: #9ca3af; margin-bottom: 12px; }
        .ladder-title { font-size: 12px; text-transform: uppercase; letter-spacing: 0.05em; color: #64748b; margin-top: 16px; font-weight: bold; }
    </style>
</head>
<body>
    <div class="container">
        <h1>SIP Simulator E2E Campaign Report</h1>
        <div class="meta">Generated on ${new Date().toLocaleString()} | Total Test Cases: ${campaignsData.length}</div>`;
        
        campaignsData.forEach((tc, idx) => {
            const badgeClass = tc.runResult === 'PASS' ? 'pass' : (tc.runResult === 'FAIL' ? 'fail' : 'pending');
            const resultText = tc.runResult || tc.runStatus || 'Not Executed';
            
            // Try to find a matched log file
            let svgLadderHtml = '';
            if (idx < mappedLogs.length) {
                const logPath = mappedLogs[idx].path;
                const msgs = parseLogToMessages(logPath);
                if (msgs.length > 0) {
                    svgLadderHtml = `<div class="ladder-title">Visual SIP Sequence Diagram</div>` + generateSvgLadder(msgs);
                }
            }
            
            reportHtml += `
            <div class="tc-card">
                <div class="tc-header">
                    <span class="tc-title">${idx + 1}. ${tc.name}</span>
                    <span class="tc-badge ${badgeClass}">${resultText}</span>
                </div>
                <div class="tc-details">
                    <div><strong>Caller Scenario:</strong> ${tc.callerScenario}</div>
                    <div><strong>Callee Scenario:</strong> ${tc.calleeScenario}</div>
                    <div><strong>Caller Values:</strong> ${tc.callerValues}</div>
                    <div><strong>Callee Values:</strong> ${tc.calleeValues}</div>
                    <div><strong>Expected Outcome:</strong> ${tc.expectedStatus}</div>
                    <div><strong>Duration:</strong> ${tc.duration ? tc.duration + 's' : '-'}</div>
                </div>
                ${svgLadderHtml}
            </div>`;
        });
        
        reportHtml += `</div>
</body>
</html>`;
        
        res.setHeader('Content-Type', 'text/html');
        res.setHeader('Content-Disposition', 'attachment; filename=sip_campaign_report.html');
        res.send(reportHtml);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// GET active RTP diagnostics
app.get('/api/rtp/stats', (req, res) => {
    const diagPath = path.join(TRUNK_DIR, 'config', 'rtp_diagnostics.json');
    if (!fs.existsSync(diagPath)) {
        return res.json({ audio_running: false });
    }
    try {
        const content = fs.readFileSync(diagPath, 'utf-8');
        res.json(JSON.parse(content));
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// GET active TLS connection diagnostics
app.get('/api/security/tls', (req, res) => {
    const tlsPath = path.join(TRUNK_DIR, 'config', 'tls_diagnostics.json');
    if (!fs.existsSync(tlsPath)) {
        return res.json({ protocol: 'None' });
    }
    try {
        const content = fs.readFileSync(tlsPath, 'utf-8');
        res.json(JSON.parse(content));
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// POST import PCAP file
app.post('/api/files/import-pcap', express.raw({ type: '*/*', limit: '50mb' }), (req, res) => {
    const tempPcapPath = path.join(TRUNK_DIR, 'config', 'uploaded_temp.pcap');
    const { callId, scenarioName, valuesName } = req.query;
    
    const scen = scenarioName || 'autogen_pcap_scenario.txt';
    const vals = valuesName || 'autogen_pcap_values.txt';
    
    const outScenarioPath = path.join(TRUNK_DIR, 'scenarios', scen);
    const outValuesPath = path.join(TRUNK_DIR, 'values', vals);
    
    try {
        const configDir = path.join(TRUNK_DIR, 'config');
        if (!fs.existsSync(configDir)) {
            fs.mkdirSync(configDir, { recursive: true });
        }
        
        fs.writeFileSync(tempPcapPath, req.body);
        
        const javaArgs = [
            '-cp',
            javaCp(),
            'sipinspector.Utils.PcapConverter',
            `"${tempPcapPath}"`,
            `"${outScenarioPath}"`,
            `"${outValuesPath}"`,
            callId ? `"${callId}"` : '""'
        ];

        const javaProc = spawn(javaBin(), javaArgs, { cwd: TRUNK_DIR, shell: true });
        trackChildProcess(javaProc);
        
        let stdout = '';
        let stderr = '';
        
        javaProc.stdout.on('data', (data) => {
            stdout += data.toString();
        });
        
        javaProc.stderr.on('data', (data) => {
            stderr += data.toString();
        });
        
        javaProc.on('close', (code) => {
            try {
                if (fs.existsSync(tempPcapPath)) {
                    fs.unlinkSync(tempPcapPath);
                }
            } catch (e) {}
            
            if (code === 0) {
                res.json({
                    success: true,
                    message: `PCAP parsed and scenario generated successfully.`,
                    scenarioName: scen,
                    valuesName: vals,
                    stdout: stdout.trim()
                });
            } else {
                res.status(500).json({
                    error: `Java process failed with exit code ${code}`,
                    stderr: stderr.trim(),
                    stdout: stdout.trim()
                });
            }
        });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// =========================================================================
// Production Call Engine & Audio Management Endpoints (Phase 4)
// =========================================================================
const CALL_ENGINE_PORT = 5065;
let daemonProcess = null;
let daemonStarting = null;

function requestDaemon(method, reqPath, body = null) {
    return new Promise((resolve, reject) => {
        const payload = body ? (typeof body === 'string' ? body : JSON.stringify(body)) : null;
        const options = {
            hostname: '127.0.0.1',
            port: CALL_ENGINE_PORT,
            path: reqPath,
            method: method,
            headers: {
                'Content-Type': 'application/json',
                ...(payload ? { 'Content-Length': Buffer.byteLength(payload) } : {})
            },
            timeout: 5000
        };

        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const parsed = JSON.parse(data);
                    resolve({ status: res.statusCode, data: parsed });
                } catch (e) {
                    resolve({ status: res.statusCode, data });
                }
            });
        });

        req.on('error', (err) => reject(err));
        req.on('timeout', () => {
            req.destroy();
            reject(new Error('Call Engine daemon request timed out'));
        });

        if (payload) {
            req.write(payload);
        }
        req.end();
    });
}

async function isDaemonAlive() {
    try {
        const res = await requestDaemon('GET', '/api/engine/status');
        return res.status === 200;
    } catch (e) {
        return false;
    }
}

async function ensureCallEngineDaemon() {
    if (await isDaemonAlive()) {
        return true;
    }

    if (daemonStarting) {
        return daemonStarting;
    }

    daemonStarting = (async () => {
        const java = javaBin();
        const args = ['-cp', javaCp(), 'sipinspector.Call.CliCallEngine', 'daemon', String(CALL_ENGINE_PORT)];
        daemonProcess = spawn(java, args, { cwd: TRUNK_DIR, shell: true });
        trackChildProcess(daemonProcess);

        daemonProcess.stdout.on('data', (d) => {
            const line = d.toString().trim();
            if (line) console.log(`[DAEMON] ${line}`);
        });
        daemonProcess.stderr.on('data', (d) => {
            const line = d.toString().trim();
            if (line) console.error(`[DAEMON-ERR] ${line}`);
        });

        // Wait up to 6 seconds for daemon to start and respond
        const start = Date.now();
        while (Date.now() - start < 6000) {
            await new Promise(r => setTimeout(r, 250));
            if (await isDaemonAlive()) {
                daemonStarting = null;
                return true;
            }
        }
        daemonStarting = null;
        throw new Error('Timed out waiting for Call Engine daemon to start on port ' + CALL_ENGINE_PORT);
    })();

    return daemonStarting;
}

// 1. Query call engine status
app.get('/api/call/engine/status', async (req, res) => {
    try {
        const alive = await isDaemonAlive();
        if (!alive) {
            return res.json({ running: false, daemon: false });
        }
        const resp = await requestDaemon('GET', '/api/engine/status');
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 2. Query active calls
app.get('/api/call/active', async (req, res) => {
    try {
        await ensureCallEngineDaemon();
        const resp = await requestDaemon('GET', '/api/call/active');
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 3. Make outbound call
app.post('/api/call/dial', async (req, res) => {
    try {
        await ensureCallEngineDaemon();
        const resp = await requestDaemon('POST', '/api/call/dial', req.body);
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 4. Answer incoming call
app.post('/api/call/answer', async (req, res) => {
    try {
        await ensureCallEngineDaemon();
        const resp = await requestDaemon('POST', '/api/call/answer', req.body);
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 5. Reject incoming call
app.post('/api/call/reject', async (req, res) => {
    try {
        await ensureCallEngineDaemon();
        const resp = await requestDaemon('POST', '/api/call/reject', req.body);
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 6. Terminate call (BYE)
app.post('/api/call/hangup', async (req, res) => {
    try {
        await ensureCallEngineDaemon();
        const resp = await requestDaemon('POST', '/api/call/hangup', req.body);
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 7. Hold / Resume call (Re-INVITE)
app.post('/api/call/hold', async (req, res) => {
    try {
        await ensureCallEngineDaemon();
        const resp = await requestDaemon('POST', '/api/call/hold', req.body);
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 8. Mute / Unmute microphone
app.post('/api/call/mute', async (req, res) => {
    try {
        await ensureCallEngineDaemon();
        const resp = await requestDaemon('POST', '/api/call/mute', req.body);
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 9. Enumerate audio devices
app.get('/api/audio/devices', async (req, res) => {
    try {
        await ensureCallEngineDaemon();
        const resp = await requestDaemon('GET', '/api/audio/devices');
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 10. Select active audio devices
app.post('/api/audio/devices', async (req, res) => {
    try {
        await ensureCallEngineDaemon();
        const resp = await requestDaemon('POST', '/api/audio/devices', req.body);
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 11. Cancel call (CANCEL)
app.post('/api/call/cancel', async (req, res) => {
    try {
        await ensureCallEngineDaemon();
        const resp = await requestDaemon('POST', '/api/call/cancel', req.body);
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 12. Start Performance Campaign
app.post('/api/perf/start', async (req, res) => {
    try {
        await ensureCallEngineDaemon();
        const resp = await requestDaemon('POST', '/api/perf/start', req.body);
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 13. Stop Performance Campaign
app.post('/api/perf/stop', async (req, res) => {
    try {
        await ensureCallEngineDaemon();
        const resp = await requestDaemon('POST', '/api/perf/stop', req.body);
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 14. Query Performance Status
app.get('/api/perf/status', async (req, res) => {
    try {
        await ensureCallEngineDaemon();
        const resp = await requestDaemon('GET', '/api/perf/status');
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 15. Query Performance Results
app.get('/api/perf/results', async (req, res) => {
    try {
        await ensureCallEngineDaemon();
        const resp = await requestDaemon('GET', '/api/perf/results');
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 16. Execute Controlled Failure Injection Test
app.post('/api/perf/failure-test', async (req, res) => {
    try {
        await ensureCallEngineDaemon();
        const resp = await requestDaemon('POST', '/api/perf/failure-test', req.body);
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 17. System Health Status
app.get('/api/health', async (req, res) => {
    try {
        const alive = await isDaemonAlive();
        if (!alive) {
            return res.json({ status: "DEGRADED", daemon: false });
        }
        const resp = await requestDaemon('GET', '/api/health');
        res.status(resp.status).json(resp.data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// Serve frontend if in production
const frontendBuildPath = path.join(__dirname, 'public');
if (fs.existsSync(frontendBuildPath)) {
    app.use(express.static(frontendBuildPath));
    app.get('*', (req, res) => {
        res.sendFile(path.join(frontendBuildPath, 'index.html'));
    });
}

app.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}`);
});
