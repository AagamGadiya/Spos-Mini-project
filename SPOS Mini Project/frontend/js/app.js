/**
 * Printer Spooler System - Frontend Application
 * SPOS Mini Project
 * 
 * Pure Vanilla JavaScript implementation:
 * - Single Page Architecture with hash/tab navigation
 * - Real-time polling and status updates
 * - REST API client communicating with Java 21 backend
 * - Interactive charts, simulation controls, and sync monitors
 */

const App = (() => {
    // --- Configuration ---
    const API_BASE = window.location.origin.includes('http') && !window.location.origin.includes('file://') 
        ? window.location.origin 
        : 'http://localhost:8080';
    
    const POLL_INTERVAL = 1000; // 1 second polling
    let pollTimer = null;
    let currentFilter = 'all';
    let isConnected = false;
    let allLogsCache = [];

    // --- DOM Elements Cache ---
    const elements = {
        // Nav & Layout
        navItems: document.querySelectorAll('.nav-item'),
        pages: document.querySelectorAll('.page'),
        pageTitle: document.getElementById('page-title'),
        pageSubtitle: document.getElementById('page-subtitle'),
        menuToggle: document.getElementById('menu-toggle'),
        sidebar: document.getElementById('sidebar'),
        serverStatus: document.getElementById('server-status'),
        speedSelect: document.getElementById('speed-select'),
        btnResetSystem: document.getElementById('btn-reset-system'),

        // Dashboard
        dashPrinterStatus: document.getElementById('dash-printer-status'),
        dashCurrentJob: document.getElementById('dash-current-job'),
        dashQueueLength: document.getElementById('dash-queue-length'),
        dashTotalJobs: document.getElementById('dash-total-jobs'),
        dashCompleted: document.getElementById('dash-completed'),
        dashCancelled: document.getElementById('dash-cancelled'),
        dashPages: document.getElementById('dash-pages'),
        dashAvgWait: document.getElementById('dash-avg-wait'),
        dashProgressCard: document.getElementById('dash-progress-card'),
        dashProgressJobId: document.getElementById('dash-progress-jobid'),
        dashProgressDoc: document.getElementById('dash-progress-doc'),
        dashProgressText: document.getElementById('dash-progress-text'),
        dashProgressBar: document.getElementById('dash-progress-bar'),
        dashProgressPages: document.getElementById('dash-progress-pages'),
        dashProgressUser: document.getElementById('dash-progress-user'),
        dashRecentTbody: document.getElementById('dash-recent-tbody'),

        // Submit Form
        submitForm: document.getElementById('submit-form'),
        inputUserId: document.getElementById('input-userid'),
        inputUserName: document.getElementById('input-username'),
        inputDocName: document.getElementById('input-docname'),
        inputPages: document.getElementById('input-pages'),
        btnClearForm: document.getElementById('btn-clear-form'),

        // Queue
        queueCount: document.getElementById('queue-count'),
        queueTbody: document.getElementById('queue-tbody'),
        allJobsTbody: document.getElementById('all-jobs-tbody'),

        // Printer
        btnPausePrinter: document.getElementById('btn-pause-printer'),
        btnResumePrinter: document.getElementById('btn-resume-printer'),
        printerStatusBadge: document.getElementById('printer-status-badge'),
        printerTotalPrinted: document.getElementById('printer-total-printed'),
        printerTotalPages: document.getElementById('printer-total-pages'),
        printerLight: document.getElementById('printer-light'),
        printerPaper: document.getElementById('printer-paper'),
        printerCurrentBody: document.getElementById('printer-current-body'),

        // Simulation
        simUsers: document.getElementById('sim-users'),
        simJobs: document.getElementById('sim-jobs'),
        simTotal: document.getElementById('sim-total'),
        btnStartSimulation: document.getElementById('btn-start-simulation'),

        // Synchronization
        syncActiveThreads: document.getElementById('sync-active-threads'),
        syncQueueLock: document.getElementById('sync-queue-lock'),
        syncPrinterLock: document.getElementById('sync-printer-lock'),
        syncCriticalSection: document.getElementById('sync-critical-section'),
        syncConcurrentSubmissions: document.getElementById('sync-concurrent-submissions'),
        syncJobsSafe: document.getElementById('sync-jobs-safe'),
        syncQueueWait: document.getElementById('sync-queue-wait'),
        syncPrinterWait: document.getElementById('sync-printer-wait'),

        // Statistics
        barChartJobs: document.getElementById('bar-chart-jobs'),
        donutCircle: document.getElementById('donut-circle'),
        donutText: document.getElementById('donut-text'),
        statsAvgWait: document.getElementById('stats-avg-wait'),
        statsAvgPrint: document.getElementById('stats-avg-print'),
        statsTotalPages: document.getElementById('stats-total-pages'),
        statusBars: document.getElementById('status-bars'),

        // Logs
        logContainer: document.getElementById('log-container'),
        logFilters: document.querySelectorAll('.log-filter'),

        // Modal & Toast
        modalOverlay: document.getElementById('modal-overlay'),
        modalClose: document.getElementById('modal-close'),
        modalBody: document.getElementById('modal-body'),
        toastContainer: document.getElementById('toast-container')
    };

    // --- Page Metadata ---
    const pageMeta = {
        'dashboard': { title: 'Dashboard', subtitle: 'System Overview & Live Metrics' },
        'submit': { title: 'Submit Job', subtitle: 'Create and Dispatch Print Jobs' },
        'queue': { title: 'Print Queue', subtitle: 'FIFO Scheduled Print Buffer' },
        'printer': { title: 'Printer Control', subtitle: 'Virtual Shared Device State' },
        'simulation': { title: 'Concurrent Simulation', subtitle: 'Demonstrating Multi-threading & OS Sync' },
        'synchronization': { title: 'Sync Monitor', subtitle: 'Locks, Critical Sections & Mutex Telemetry' },
        'statistics': { title: 'Statistics & Analytics', subtitle: 'Throughput & Performance Measurements' },
        'logs': { title: 'Activity Log', subtitle: 'System-wide Execution Audit Trail' },
        'about': { title: 'About Project', subtitle: 'SPOS Architecture & Concepts' }
    };

    // --- API Helper Methods ---
    async function apiRequest(endpoint, options = {}) {
        const url = `${API_BASE}${endpoint}`;
        const config = {
            headers: {
                'Content-Type': 'application/json',
                ...(options.headers || {})
            },
            ...options
        };

        try {
            const response = await fetch(url, config);
            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                const errMsg = data.error || `HTTP error ${response.status}`;
                throw new Error(errMsg);
            }
            setConnectionStatus(true);
            return data;
        } catch (error) {
            if (error.message.includes('Failed to fetch') || error.message.includes('NetworkError')) {
                setConnectionStatus(false);
            }
            throw error;
        }
    }

    function setConnectionStatus(connected) {
        if (isConnected === connected) return;
        isConnected = connected;
        if (!elements.serverStatus) return;

        if (connected) {
            elements.serverStatus.classList.remove('offline');
            elements.serverStatus.classList.add('online');
            elements.serverStatus.innerHTML = '<span class="status-dot online"></span><span>Connected</span>';
        } else {
            elements.serverStatus.classList.remove('online');
            elements.serverStatus.classList.add('offline');
            elements.serverStatus.innerHTML = '<span class="status-dot offline"></span><span>Disconnected</span>';
        }
    }

    // --- Toast Notifications ---
    function showToast(message, type = 'info', title = '') {
        if (!elements.toastContainer) return;

        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        
        const iconSvg = type === 'success' 
            ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>'
            : type === 'error'
            ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>'
            : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>';

        toast.innerHTML = `
            <div class="toast-icon">${iconSvg}</div>
            <div class="toast-content">
                ${title ? `<div class="toast-title">${title}</div>` : ''}
                <div class="toast-message">${escapeHtml(message)}</div>
            </div>
            <button class="toast-close" onclick="this.parentElement.remove()">&times;</button>
        `;

        elements.toastContainer.appendChild(toast);

        // Auto remove after 4.5 seconds
        setTimeout(() => {
            if (toast.parentElement) {
                toast.classList.add('fade-out');
                setTimeout(() => toast.remove(), 300);
            }
        }, 4500);
    }

    // --- Utility Functions ---
    function escapeHtml(str) {
        if (str === null || str === undefined) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function formatTimeMs(ms) {
        if (ms == null || isNaN(ms)) return '0s';
        if (ms < 1000) return `${ms}ms`;
        const sec = (ms / 1000).toFixed(1);
        if (sec < 60) return `${sec}s`;
        const min = Math.floor(sec / 60);
        const remSec = (sec % 60).toFixed(0);
        return `${min}m ${remSec}s`;
    }

    function getBadgeClass(status) {
        switch ((status || '').toUpperCase()) {
            case 'PRINTING': return 'badge-printing';
            case 'COMPLETED': return 'badge-completed';
            case 'QUEUED': return 'badge-queued';
            case 'CANCELLED': return 'badge-cancelled';
            case 'PAUSED': return 'badge-paused';
            case 'IDLE': return 'badge-idle';
            default: return 'badge-secondary';
        }
    }

    // --- Navigation Handling ---
    function switchPage(pageId) {
        elements.navItems.forEach(item => {
            if (item.getAttribute('data-page') === pageId) {
                item.classList.add('active');
            } else {
                item.classList.remove('active');
            }
        });

        elements.pages.forEach(page => {
            if (page.id === `page-${pageId}`) {
                page.classList.add('active');
            } else {
                page.classList.remove('active');
            }
        });

        if (pageMeta[pageId]) {
            if (elements.pageTitle) elements.pageTitle.textContent = pageMeta[pageId].title;
            if (elements.pageSubtitle) elements.pageSubtitle.textContent = pageMeta[pageId].subtitle;
        }

        // Close sidebar on mobile after choosing
        if (elements.sidebar && window.innerWidth < 992) {
            elements.sidebar.classList.remove('open');
        }

        // Trigger immediate fetch for this page
        fetchCurrentPageData(pageId);
    }

    function fetchCurrentPageData(pageId) {
        switch (pageId) {
            case 'dashboard':
                updateDashboard();
                break;
            case 'queue':
                updateQueuePage();
                break;
            case 'printer':
                updatePrinterPage();
                break;
            case 'synchronization':
                updateSyncPage();
                break;
            case 'statistics':
                updateStatisticsPage();
                break;
            case 'logs':
                updateLogsPage();
                break;
        }
    }

    // --- Data Fetching & UI Updaters ---

    // 1. Dashboard Updater
    async function updateDashboard() {
        try {
            const data = await apiRequest('/api/status');
            const printer = data.printer || {};
            const stats = data.statistics || {};
            const queueSize = data.queueSize || 0;

            // Stat Cards
            if (elements.dashPrinterStatus) {
                elements.dashPrinterStatus.textContent = printer.statusDisplay || printer.status || 'IDLE';
                elements.dashPrinterStatus.className = `stat-value status-${(printer.status || 'idle').toLowerCase()}`;
            }

            if (elements.dashCurrentJob) {
                if (printer.currentJob) {
                    elements.dashCurrentJob.textContent = `#${printer.currentJob.jobId} - ${printer.currentJob.documentName}`;
                    elements.dashCurrentJob.title = printer.currentJob.documentName;
                } else {
                    elements.dashCurrentJob.textContent = 'None';
                    elements.dashCurrentJob.title = '';
                }
            }

            if (elements.dashQueueLength) elements.dashQueueLength.textContent = queueSize;
            if (elements.dashTotalJobs) elements.dashTotalJobs.textContent = stats.totalJobsSubmitted || 0;
            if (elements.dashCompleted) elements.dashCompleted.textContent = stats.completedJobs || 0;
            if (elements.dashCancelled) elements.dashCancelled.textContent = stats.cancelledJobs || 0;
            if (elements.dashPages) elements.dashPages.textContent = stats.totalPagesCompleted || 0;
            if (elements.dashAvgWait) elements.dashAvgWait.textContent = formatTimeMs(stats.averageWaitingTimeMs);

            // Live progress card for active job
            if (printer.currentJob && printer.status === 'PRINTING') {
                const job = printer.currentJob;
                if (elements.dashProgressCard) elements.dashProgressCard.style.display = 'block';
                if (elements.dashProgressJobId) elements.dashProgressJobId.textContent = job.jobId;
                if (elements.dashProgressDoc) elements.dashProgressDoc.textContent = job.documentName;
                const percent = job.progressPercent || 0;
                if (elements.dashProgressText) elements.dashProgressText.textContent = `${percent}%`;
                if (elements.dashProgressBar) elements.dashProgressBar.style.width = `${percent}%`;
                if (elements.dashProgressPages) elements.dashProgressPages.textContent = `${job.printedPages || 0}/${job.pages}`;
                if (elements.dashProgressUser) elements.dashProgressUser.textContent = `${job.userName} (${job.userId})`;
            } else {
                if (elements.dashProgressCard) elements.dashProgressCard.style.display = 'none';
            }

            // Recent Jobs in Dashboard
            const jobsData = await apiRequest('/api/jobs');
            const jobs = jobsData.jobs || [];
            renderRecentJobs(jobs.slice(-5).reverse());

        } catch (e) {
            // Silently handled in API helper (sets connection status)
        }
    }

    function renderRecentJobs(jobs) {
        if (!elements.dashRecentTbody) return;
        if (!jobs || jobs.length === 0) {
            elements.dashRecentTbody.innerHTML = '<tr><td colspan="5" class="empty-state">No jobs yet</td></tr>';
            return;
        }

        elements.dashRecentTbody.innerHTML = jobs.map(job => `
            <tr>
                <td><strong>#${escapeHtml(job.jobId)}</strong></td>
                <td>${escapeHtml(job.userName)} <span class="text-muted">(${escapeHtml(job.userId)})</span></td>
                <td>${escapeHtml(job.documentName)}</td>
                <td>${job.pages} pages</td>
                <td><span class="badge ${getBadgeClass(job.status)}">${escapeHtml(job.statusDisplay || job.status)}</span></td>
            </tr>
        `).join('');
    }

    // 2. Queue Page Updater
    async function updateQueuePage() {
        try {
            const queueData = await apiRequest('/api/queue');
            const allJobsData = await apiRequest('/api/jobs');

            const queue = queueData.queue || [];
            const allJobs = allJobsData.jobs || [];

            if (elements.queueCount) {
                elements.queueCount.textContent = `${queue.length} ${queue.length === 1 ? 'job' : 'jobs'}`;
            }

            // Render Queue Table
            if (elements.queueTbody) {
                if (queue.length === 0) {
                    elements.queueTbody.innerHTML = '<tr><td colspan="8" class="empty-state">Queue is empty. Submit a job to see it here!</td></tr>';
                } else {
                    elements.queueTbody.innerHTML = queue.map((job, idx) => `
                        <tr>
                            <td>${idx + 1}</td>
                            <td><strong>#${escapeHtml(job.jobId)}</strong></td>
                            <td>${escapeHtml(job.userName)}</td>
                            <td>${escapeHtml(job.documentName)}</td>
                            <td>${job.pages}</td>
                            <td>${escapeHtml(job.submissionTime)}</td>
                            <td><span class="badge ${getBadgeClass(job.status)}">${escapeHtml(job.statusDisplay || job.status)}</span></td>
                            <td>
                                <button class="btn btn-danger btn-sm" onclick="App.cancelJob('${escapeHtml(job.jobId)}')">
                                    Cancel
                                </button>
                            </td>
                        </tr>
                    `).join('');
                }
            }

            // Render All Jobs History Table
            if (elements.allJobsTbody) {
                if (allJobs.length === 0) {
                    elements.allJobsTbody.innerHTML = '<tr><td colspan="8" class="empty-state">No jobs submitted yet</td></tr>';
                } else {
                    // Newest first
                    const reversed = [...allJobs].reverse();
                    elements.allJobsTbody.innerHTML = reversed.map(job => `
                        <tr>
                            <td><strong>#${escapeHtml(job.jobId)}</strong></td>
                            <td>${escapeHtml(job.userName)} <span class="text-muted">(${escapeHtml(job.userId)})</span></td>
                            <td>${escapeHtml(job.documentName)}</td>
                            <td>${job.pages}</td>
                            <td>${escapeHtml(job.submissionTime)}</td>
                            <td><span class="badge ${getBadgeClass(job.status)}">${escapeHtml(job.statusDisplay || job.status)}</span></td>
                            <td>${formatTimeMs(job.waitingTimeMs)}</td>
                            <td>
                                <button class="btn btn-outline btn-sm" onclick="App.showJobModal('${escapeHtml(job.jobId)}')">
                                    Details
                                </button>
                            </td>
                        </tr>
                    `).join('');
                }
            }
        } catch (e) {
            // Handled
        }
    }

    // 3. Printer Page Updater
    async function updatePrinterPage() {
        try {
            const data = await apiRequest('/api/status');
            const printer = data.printer || {};
            const status = (printer.status || 'IDLE').toUpperCase();

            if (elements.printerStatusBadge) {
                elements.printerStatusBadge.textContent = printer.statusDisplay || status;
                elements.printerStatusBadge.className = `badge ${getBadgeClass(status)}`;
            }

            if (elements.printerTotalPrinted) {
                elements.printerTotalPrinted.textContent = `${printer.totalJobsPrinted || 0} jobs`;
            }

            if (elements.printerTotalPages) {
                elements.printerTotalPages.textContent = `${printer.totalPagesPrinted || 0}`;
            }

            // Visual Hardware Simulation
            if (elements.printerLight) {
                if (status === 'PRINTING') {
                    elements.printerLight.setAttribute('fill', '#10b981'); // Green
                } else if (status === 'PAUSED') {
                    elements.printerLight.setAttribute('fill', '#f59e0b'); // Amber
                } else {
                    elements.printerLight.setAttribute('fill', '#94a3b8'); // Gray
                }
            }

            if (elements.printerPaper) {
                if (status === 'PRINTING') {
                    elements.printerPaper.classList.add('paper-printing');
                } else {
                    elements.printerPaper.classList.remove('paper-printing');
                }
            }

            // Current Job Display
            if (elements.printerCurrentBody) {
                if (printer.currentJob) {
                    const job = printer.currentJob;
                    const percent = job.progressPercent || 0;
                    elements.printerCurrentBody.innerHTML = `
                        <div class="current-job-active">
                            <div class="current-job-header">
                                <div>
                                    <h4 class="mb-1">${escapeHtml(job.documentName)}</h4>
                                    <span class="text-muted">Job ID: <strong>#${escapeHtml(job.jobId)}</strong> &bull; User: <strong>${escapeHtml(job.userName)}</strong> (${escapeHtml(job.userId)})</span>
                                </div>
                                <span class="badge ${getBadgeClass(job.status)}">${escapeHtml(job.statusDisplay || job.status)}</span>
                            </div>
                            <div class="progress-info mt-3">
                                <span>Progress (${job.printedPages || 0} of ${job.pages} pages printed)</span>
                                <strong>${percent}%</strong>
                            </div>
                            <div class="progress-bar-container">
                                <div class="progress-bar" style="width: ${percent}%"></div>
                            </div>
                            <div class="current-job-footer mt-3">
                                <div>Submitted: <strong>${escapeHtml(job.submissionTime)}</strong></div>
                                <div>Elapsed Printing Time: <strong>${formatTimeMs(job.printingTimeMs)}</strong></div>
                            </div>
                        </div>
                    `;
                } else {
                    elements.printerCurrentBody.innerHTML = `
                        <div class="empty-state">
                            <p>Printer is currently <strong>${status}</strong>.</p>
                            <span class="text-muted">No active print job in progress.</span>
                        </div>
                    `;
                }
            }

            // Enable/disable pause/resume buttons
            if (elements.btnPausePrinter && elements.btnResumePrinter) {
                if (status === 'PAUSED') {
                    elements.btnPausePrinter.disabled = true;
                    elements.btnResumePrinter.disabled = false;
                } else {
                    elements.btnPausePrinter.disabled = false;
                    elements.btnResumePrinter.disabled = true;
                }
            }

        } catch (e) {
            // Handled
        }
    }

    // 4. Synchronization Monitor Updater
    async function updateSyncPage() {
        try {
            const data = await apiRequest('/api/synchronization');
            const sync = data.sync || {};

            if (elements.syncActiveThreads) {
                elements.syncActiveThreads.textContent = sync.activeThreads != null ? sync.activeThreads : 0;
            }

            if (elements.syncQueueLock) {
                const isLocked = sync.queueLockLocked;
                elements.syncQueueLock.innerHTML = isLocked 
                    ? '<span class="status-badge status-locked">Locked (In Critical Section)</span>'
                    : '<span class="status-badge status-unlocked">Unlocked (Available)</span>';
            }

            if (elements.syncPrinterLock) {
                const isLocked = sync.printerLockLocked;
                elements.syncPrinterLock.innerHTML = isLocked
                    ? '<span class="status-badge status-locked">Locked (Mutual Exclusion Active)</span>'
                    : '<span class="status-badge status-unlocked">Unlocked (Idle / Waiting)</span>';
            }

            if (elements.syncCriticalSection) {
                if (sync.printerLockLocked && sync.currentJobId) {
                    elements.syncCriticalSection.innerHTML = `<span class="badge badge-printing">Busy (Printing #${escapeHtml(sync.currentJobId)})</span>`;
                } else {
                    elements.syncCriticalSection.innerHTML = '<span class="badge badge-idle">Idle (No thread inside)</span>';
                }
            }

            if (elements.syncConcurrentSubmissions) {
                elements.syncConcurrentSubmissions.textContent = sync.totalConcurrentSubmissions || 0;
            }

            if (elements.syncJobsSafe) {
                elements.syncJobsSafe.textContent = sync.jobsProcessedSafely || 0;
            }

            if (elements.syncQueueWait) {
                elements.syncQueueWait.textContent = sync.queueLockQueueLength || 0;
            }

            if (elements.syncPrinterWait) {
                elements.syncPrinterWait.textContent = sync.printerLockQueueLength || 0;
            }

        } catch (e) {
            // Handled
        }
    }

    // 5. Statistics Page Updater
    async function updateStatisticsPage() {
        try {
            const data = await apiRequest('/api/statistics');
            const stats = data.statistics || {};

            const submitted = stats.totalJobsSubmitted || 0;
            const completed = stats.completedJobs || 0;
            const cancelled = stats.cancelledJobs || 0;
            const failed = stats.failedJobs || 0;
            const pending = stats.pendingJobs || 0;
            const utilization = parseFloat(stats.printerUtilization || 0);

            // Bar Chart (Jobs Overview)
            if (elements.barChartJobs) {
                const maxVal = Math.max(submitted, 1);
                elements.barChartJobs.innerHTML = `
                    <div class="bar-col">
                        <div class="bar-fill bar-primary" style="height: ${(submitted / maxVal) * 100}%" title="Submitted: ${submitted}">
                            <span class="bar-val">${submitted}</span>
                        </div>
                        <span class="bar-label">Submitted</span>
                    </div>
                    <div class="bar-col">
                        <div class="bar-fill bar-success" style="height: ${(completed / maxVal) * 100}%" title="Completed: ${completed}">
                            <span class="bar-val">${completed}</span>
                        </div>
                        <span class="bar-label">Completed</span>
                    </div>
                    <div class="bar-col">
                        <div class="bar-fill bar-warning" style="height: ${(pending / maxVal) * 100}%" title="Pending: ${pending}">
                            <span class="bar-val">${pending}</span>
                        </div>
                        <span class="bar-label">Pending</span>
                    </div>
                    <div class="bar-col">
                        <div class="bar-fill bar-danger" style="height: ${(cancelled / maxVal) * 100}%" title="Cancelled: ${cancelled}">
                            <span class="bar-val">${cancelled}</span>
                        </div>
                        <span class="bar-label">Cancelled</span>
                    </div>
                `;
            }

            // Donut Chart (Utilization)
            if (elements.donutCircle && elements.donutText) {
                const circumference = 2 * Math.PI * 50; // r=50 -> ~314.16
                const offset = circumference - (utilization / 100) * circumference;
                elements.donutCircle.style.strokeDashoffset = offset;
                elements.donutText.textContent = `${utilization.toFixed(1)}%`;
            }

            // Time & Metrics
            if (elements.statsAvgWait) elements.statsAvgWait.textContent = formatTimeMs(stats.averageWaitingTimeMs);
            if (elements.statsAvgPrint) elements.statsAvgPrint.textContent = formatTimeMs(stats.averagePrintingTimeMs);
            if (elements.statsTotalPages) elements.statsTotalPages.textContent = stats.totalPagesCompleted || 0;

            // Status Distribution Bars
            if (elements.statusBars) {
                const compPct = submitted > 0 ? ((completed / submitted) * 100).toFixed(1) : 0;
                const pendPct = submitted > 0 ? ((pending / submitted) * 100).toFixed(1) : 0;
                const cancPct = submitted > 0 ? ((cancelled / submitted) * 100).toFixed(1) : 0;

                elements.statusBars.innerHTML = `
                    <div class="distribution-row">
                        <div class="dist-header">
                            <span>Completed</span>
                            <strong>${completed} (${compPct}%)</strong>
                        </div>
                        <div class="progress-bar-container">
                            <div class="progress-bar bg-success" style="width: ${compPct}%"></div>
                        </div>
                    </div>
                    <div class="distribution-row mt-2">
                        <div class="dist-header">
                            <span>Queued / Pending</span>
                            <strong>${pending} (${pendPct}%)</strong>
                        </div>
                        <div class="progress-bar-container">
                            <div class="progress-bar bg-warning" style="width: ${pendPct}%"></div>
                        </div>
                    </div>
                    <div class="distribution-row mt-2">
                        <div class="dist-header">
                            <span>Cancelled</span>
                            <strong>${cancelled} (${cancPct}%)</strong>
                        </div>
                        <div class="progress-bar-container">
                            <div class="progress-bar bg-danger" style="width: ${cancPct}%"></div>
                        </div>
                    </div>
                `;
            }

        } catch (e) {
            // Handled
        }
    }

    // 6. Activity Log Updater
    async function updateLogsPage() {
        try {
            const data = await apiRequest('/api/logs');
            allLogsCache = data.logs || [];
            renderFilteredLogs();
        } catch (e) {
            // Handled
        }
    }

    function renderFilteredLogs() {
        if (!elements.logContainer) return;

        let filtered = allLogsCache;
        if (currentFilter !== 'all') {
            filtered = allLogsCache.filter(log => (log.type || '').toUpperCase() === currentFilter.toUpperCase());
        }

        if (filtered.length === 0) {
            elements.logContainer.innerHTML = '<div class="empty-state">No activity logs matching this filter.</div>';
            return;
        }

        elements.logContainer.innerHTML = filtered.map(log => {
            const type = (log.type || 'INFO').toUpperCase();
            let badgeClass = 'badge-secondary';
            if (type === 'SUBMIT') badgeClass = 'badge-info';
            else if (type === 'PRINT') badgeClass = 'badge-printing';
            else if (type === 'COMPLETE') badgeClass = 'badge-completed';
            else if (type === 'CANCEL') badgeClass = 'badge-cancelled';
            else if (type === 'SYNC') badgeClass = 'badge-warning';
            else if (type === 'SYSTEM') badgeClass = 'badge-primary';
            else if (type === 'ERROR') badgeClass = 'badge-danger';

            return `
                <div class="log-entry log-${type.toLowerCase()}">
                    <span class="log-time">${escapeHtml(log.timestamp)}</span>
                    <span class="badge ${badgeClass} log-badge">${type}</span>
                    <span class="log-msg">${escapeHtml(log.message)}</span>
                </div>
            `;
        }).join('');
    }

    // --- Interactive User Actions ---

    // Quick Fill helper called from sample data buttons
    function quickFill(userId, userName, docName, pages) {
        switchPage('submit');
        if (elements.inputUserId) elements.inputUserId.value = userId;
        if (elements.inputUserName) elements.inputUserName.value = userName;
        if (elements.inputDocName) elements.inputDocName.value = docName;
        if (elements.inputPages) elements.inputPages.value = pages;
        showToast(`Form filled with sample job for ${userName}`, 'info', 'Quick Fill');
    }

    // Submit print job
    async function handleJobSubmit(e) {
        e.preventDefault();
        const userId = elements.inputUserId.value.trim();
        const userName = elements.inputUserName.value.trim();
        const documentName = elements.inputDocName.value.trim();
        const pages = parseInt(elements.inputPages.value, 10);

        if (!userId || !userName || !documentName || isNaN(pages) || pages <= 0) {
            showToast('Please fill in all required fields with valid values.', 'error', 'Validation Error');
            return;
        }

        try {
            const res = await apiRequest('/api/jobs', {
                method: 'POST',
                body: JSON.stringify({ userId, userName, documentName, pages })
            });

            showToast(`Job #${res.job.jobId} ("${res.job.documentName}") submitted successfully!`, 'success', 'Job Submitted');
            elements.submitForm.reset();
            
            // Switch to queue page to show newly queued job
            switchPage('queue');
        } catch (err) {
            showToast(err.message, 'error', 'Submission Failed');
        }
    }

    // Cancel job
    async function cancelJob(jobId) {
        if (!confirm(`Are you sure you want to cancel job #${jobId}?`)) {
            return;
        }

        try {
            const res = await apiRequest(`/api/jobs/${encodeURIComponent(jobId)}`, {
                method: 'DELETE'
            });
            showToast(res.message || `Job #${jobId} cancelled`, 'success', 'Cancelled');
            updateQueuePage();
        } catch (err) {
            showToast(err.message, 'error', 'Cancel Error');
        }
    }

    // View Job Details Modal
    async function showJobModal(jobId) {
        if (!elements.modalOverlay || !elements.modalBody) return;
        
        try {
            const res = await apiRequest(`/api/jobs/${encodeURIComponent(jobId)}`);
            const job = res.job;
            if (!job) throw new Error('Job not found');

            elements.modalBody.innerHTML = `
                <div class="modal-job-detail">
                    <div class="modal-job-header">
                        <h4>Job #${escapeHtml(job.jobId)}</h4>
                        <span class="badge ${getBadgeClass(job.status)}">${escapeHtml(job.statusDisplay || job.status)}</span>
                    </div>
                    <table class="detail-table mt-3">
                        <tr><td><strong>Document:</strong></td><td>${escapeHtml(job.documentName)}</td></tr>
                        <tr><td><strong>User Name:</strong></td><td>${escapeHtml(job.userName)}</td></tr>
                        <tr><td><strong>User ID:</strong></td><td>${escapeHtml(job.userId)}</td></tr>
                        <tr><td><strong>Total Pages:</strong></td><td>${job.pages}</td></tr>
                        <tr><td><strong>Pages Printed:</strong></td><td>${job.printedPages || 0}</td></tr>
                        <tr><td><strong>Submission Time:</strong></td><td>${escapeHtml(job.submissionTime || '--')}</td></tr>
                        <tr><td><strong>Start Time:</strong></td><td>${escapeHtml(job.startTime || 'Not started')}</td></tr>
                        <tr><td><strong>Completion Time:</strong></td><td>${escapeHtml(job.completionTime || (job.status === 'COMPLETED' ? 'Done' : 'Pending'))}</td></tr>
                        <tr><td><strong>Waiting Time:</strong></td><td>${formatTimeMs(job.waitingTimeMs)}</td></tr>
                        <tr><td><strong>Printing Time:</strong></td><td>${formatTimeMs(job.printingTimeMs)}</td></tr>
                    </table>
                </div>
            `;
            elements.modalOverlay.classList.add('open');
        } catch (err) {
            showToast(err.message, 'error', 'Error Loading Job');
        }
    }

    function closeModal() {
        if (elements.modalOverlay) {
            elements.modalOverlay.classList.remove('open');
        }
    }

    // Start Simulation
    async function startSimulation() {
        const numUsers = parseInt(elements.simUsers.value, 10);
        const jobsPerUser = parseInt(elements.simJobs.value, 10);

        if (isNaN(numUsers) || numUsers < 1 || numUsers > 20) {
            showToast('Number of users must be between 1 and 20', 'error');
            return;
        }
        if (isNaN(jobsPerUser) || jobsPerUser < 1 || jobsPerUser > 10) {
            showToast('Jobs per user must be between 1 and 10', 'error');
            return;
        }

        try {
            elements.btnStartSimulation.disabled = true;
            elements.btnStartSimulation.textContent = 'Simulating...';

            const res = await apiRequest('/api/simulation/start', {
                method: 'POST',
                body: JSON.stringify({ numUsers, jobsPerUser })
            });

            showToast(`Launched concurrent simulation with ${numUsers} user threads (${res.expectedTotalJobs} jobs total)!`, 'success', 'Simulation Running');

            // Switch to sync monitor or dashboard to observe concurrency
            setTimeout(() => {
                switchPage('synchronization');
            }, 600);

        } catch (err) {
            showToast(err.message, 'error', 'Simulation Error');
        } finally {
            setTimeout(() => {
                if (elements.btnStartSimulation) {
                    elements.btnStartSimulation.disabled = false;
                    elements.btnStartSimulation.innerHTML = `
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                        Start Concurrent Simulation
                    `;
                }
            }, 2000);
        }
    }

    // Printer Pause / Resume
    async function pausePrinter() {
        try {
            await apiRequest('/api/printer/pause', { method: 'POST' });
            showToast('Printer paused successfully. Jobs will accumulate in queue.', 'warning', 'Printer Paused');
            updatePrinterPage();
        } catch (err) {
            showToast(err.message, 'error');
        }
    }

    async function resumePrinter() {
        try {
            await apiRequest('/api/printer/resume', { method: 'POST' });
            showToast('Printer resumed. Continuing print queue processing.', 'success', 'Printer Resumed');
            updatePrinterPage();
        } catch (err) {
            showToast(err.message, 'error');
        }
    }

    // Reset System
    async function resetSystem() {
        if (!confirm('Are you sure you want to RESET the entire printer spooler system? All jobs, queues, and statistics will be cleared.')) {
            return;
        }

        try {
            await apiRequest('/api/reset', { method: 'POST' });
            showToast('System has been reset to initial state.', 'info', 'System Reset');
            switchPage('dashboard');
        } catch (err) {
            showToast(err.message, 'error', 'Reset Failed');
        }
    }

    // Speed change
    async function changeSpeed() {
        if (!elements.speedSelect) return;
        const speed = elements.speedSelect.value;
        try {
            await apiRequest('/api/speed', {
                method: 'POST',
                body: JSON.stringify({ speed })
            });
            showToast(`Simulation speed set to ${speed.toUpperCase()}`, 'info', 'Speed Changed');
        } catch (err) {
            showToast(err.message, 'error');
        }
    }

    // --- Periodic Heartbeat & Polling ---
    function startPolling() {
        if (pollTimer) clearInterval(pollTimer);
        pollTimer = setInterval(() => {
            const activePageEl = document.querySelector('.page.active');
            const activePageId = activePageEl ? activePageEl.id.replace('page-', '') : 'dashboard';
            fetchCurrentPageData(activePageId);
        }, POLL_INTERVAL);
    }

    // --- Initialization & Event Listeners ---
    function init() {
        // Nav Links
        elements.navItems.forEach(item => {
            item.addEventListener('click', (e) => {
                e.preventDefault();
                const pageId = item.getAttribute('data-page');
                switchPage(pageId);
            });
        });

        // Mobile Toggle
        if (elements.menuToggle && elements.sidebar) {
            elements.menuToggle.addEventListener('click', () => {
                elements.sidebar.classList.toggle('open');
            });
        }

        // Reset System
        if (elements.btnResetSystem) {
            elements.btnResetSystem.addEventListener('click', resetSystem);
        }

        // Speed Select
        if (elements.speedSelect) {
            elements.speedSelect.addEventListener('change', changeSpeed);
        }

        // Submit Form
        if (elements.submitForm) {
            elements.submitForm.addEventListener('submit', handleJobSubmit);
        }
        if (elements.btnClearForm) {
            elements.btnClearForm.addEventListener('click', () => {
                if (elements.submitForm) elements.submitForm.reset();
            });
        }

        // Simulation Inputs
        const updateSimTotal = () => {
            const u = parseInt(elements.simUsers.value, 10) || 0;
            const j = parseInt(elements.simJobs.value, 10) || 0;
            if (elements.simTotal) elements.simTotal.textContent = u * j;
        };
        if (elements.simUsers) elements.simUsers.addEventListener('input', updateSimTotal);
        if (elements.simJobs) elements.simJobs.addEventListener('input', updateSimTotal);
        if (elements.btnStartSimulation) {
            elements.btnStartSimulation.addEventListener('click', startSimulation);
        }

        // Printer Pause / Resume
        if (elements.btnPausePrinter) {
            elements.btnPausePrinter.addEventListener('click', pausePrinter);
        }
        if (elements.btnResumePrinter) {
            elements.btnResumePrinter.addEventListener('click', resumePrinter);
        }

        // Log Filters
        elements.logFilters.forEach(btn => {
            btn.addEventListener('click', () => {
                elements.logFilters.forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                currentFilter = btn.getAttribute('data-filter') || 'all';
                renderFilteredLogs();
            });
        });

        // Modal Close
        if (elements.modalClose) {
            elements.modalClose.addEventListener('click', closeModal);
        }
        if (elements.modalOverlay) {
            elements.modalOverlay.addEventListener('click', (e) => {
                if (e.target === elements.modalOverlay) closeModal();
            });
        }
        window.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') closeModal();
        });

        // Check health and initialize initial page
        switchPage('dashboard');
        startPolling();
    }

    // Expose public API on window.App
    return {
        init,
        switchPage,
        quickFill,
        cancelJob,
        showJobModal,
        closeModal
    };
})();

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    App.init();
});
