# Printer Spooler System (SPOS Mini Project)

An interactive, web-based demonstration of **Operating System Process Synchronization, Mutual Exclusion, and Resource Scheduling** built using **Java 21 (Backend)** and **Vanilla HTML5, CSS3, & JavaScript (Frontend)**.

---

## 📌 Project Overview

In multi-tasking operating systems, a printer is a **shared non-shareable resource** (it cannot print two documents simultaneously). If multiple processes write to the printer concurrently without synchronization, documents will interleave and corrupt. 

A **Printer Spooler (Simultaneous Peripheral Operations On-Line)** solves this:
1. Multiple user processes (**Producers**) submit print jobs to a temporary buffer (the **Spooler Queue**).
2. The spooler accepts jobs without blocking the user processes.
3. A background printer daemon (**Consumer**) picks jobs one by one according to a scheduling algorithm (FIFO) and prints them on the physical/virtual printer.

This project implements and visualizes these core OS concepts in real time.

---

## 🛠️ Technology Stack

- **Backend:** Java 21
  - Built-in `com.sun.net.httpserver.HttpServer` (zero external dependencies like Spring Boot or Node.js)
  - `java.util.concurrent.locks.ReentrantLock` for mutual exclusion
  - `java.util.concurrent.locks.Condition` for producer-consumer signaling
  - `java.util.concurrent.ExecutorService` for thread pools and concurrent user simulations
  - In-memory data structures (`LinkedList`, `ArrayList`, `AtomicInteger`, `AtomicLong`)
- **Frontend:**
  - Modern HTML5 semantic layout
  - Vanilla CSS3 with custom design tokens, dark mode aesthetic, and responsive grid
  - Vanilla JavaScript (SPA architecture, live polling, DOM manipulation, SVG animations)
- **Communication:** HTTP REST-like JSON API

---

## 🧠 Core SPOS Concepts Demonstrated

| OS Concept | How It Is Implemented |
|---|---|
| **Mutual Exclusion (Mutex)** | `printerLock` (`ReentrantLock`) ensures only one thread accesses the virtual printer at any moment. |
| **Critical Section** | The printing routine between `printerLock.lock()` and `printerLock.unlock()`. |
| **Producer-Consumer Pattern** | User threads act as producers enqueueing jobs; the printer worker thread acts as the consumer dequeueing and printing. |
| **Thread Synchronization** | `Condition jobAvailable` enables the consumer to `await()` when the queue is empty, and producers to `signal()` when a new job arrives (avoiding busy-waiting). |
| **Race Condition Prevention** | `PrintQueueManager` uses a dedicated `queueLock` to synchronize all enqueue/dequeue/cancel operations on the shared queue. |
| **Process / Job Lifecycle** | Jobs transition across explicit states: `QUEUED` &rarr; `PRINTING` &rarr; `COMPLETED` / `CANCELLED`. |
| **Performance Metrics** | Tracks CPU/device utilization percentage, average waiting time, average turnaround time, and throughput. |

---

## 📂 Architecture Diagram

```
Browser (Client)
  │
  ├── HTML5 / CSS3 / Vanilla JS (SPA Dashboard)
  │     │
  │     ▼ HTTP REST API (JSON) - Port 8080
  │
Java Backend (Server)
  │
  ├── HttpServerManager (com.sun.net.httpserver)
  │     ├── Static File Server (/*)
  │     └── ApiController (/api/*)
  │           │
  │           ▼
  └── PrinterSpooler (Core Service)
        │
        ├── ReentrantLock (printerLock)
        ├── Condition (jobAvailable)
        │
        ├── PrintQueueManager (Shared Queue + queueLock)
        ├── StatisticsManager (Atomic counters & utilization)
        ├── ActivityLogger (Thread-safe system event audit log)
        │
        └── Virtual Printer Worker Thread (Consumer)
```

---

## 🚀 How to Run

### Method 1: One-Click Launch (Windows)
Double-click `run.bat` in the project root. It will compile the backend, start the HTTP server on port 8080, and open the dashboard in your default browser.

### Method 2: Command Line
1. Open PowerShell or Command Prompt in the `backend` folder:
   ```bash
   cd "backend"
   ```
2. Compile all Java classes:
   ```bash
   javac -encoding UTF-8 -d out src/spooler/model/*.java src/spooler/util/*.java src/spooler/service/*.java src/spooler/controller/*.java src/spooler/server/*.java src/spooler/Main.java
   ```
3. Run the application:
   ```bash
   java -cp out spooler.Main
   ```
4. Open your browser and navigate to:
   ```
   http://localhost:8080
   ```

---

## 📡 REST API Reference

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/health` | Health check endpoint |
| `GET` | `/api/status` | System overview, printer state, queue size, and metrics |
| `GET` | `/api/jobs` | Retrieve all jobs (queued and historical) |
| `POST` | `/api/jobs` | Submit a new print job (`userId`, `userName`, `documentName`, `pages`) |
| `GET` | `/api/jobs/{id}` | Get specific job status and turnaround times |
| `DELETE` | `/api/jobs/{id}` | Cancel a queued print job |
| `GET` | `/api/queue` | Get current jobs waiting in the print queue |
| `POST` | `/api/simulation/start` | Launch concurrent multi-user producer thread simulation |
| `POST` | `/api/printer/pause` | Temporarily pause printer execution |
| `POST` | `/api/printer/resume` | Resume printer execution |
| `GET` | `/api/statistics` | Utilization, average wait/print time, job counts |
| `GET` | `/api/synchronization` | Live mutex, lock status, and race condition prevention telemetry |
| `GET` | `/api/logs` | Real-time audit log of all system and sync events |
| `POST` | `/api/reset` | Clear all queues and reset system state |
| `POST` | `/api/speed` | Change simulation speed (`fast`, `normal`, `slow`) |

---

## 🎓 Viva Questions & Answers (SPOS Examination)

**Q1: What is spooling, and why is it needed?**
> *Answer:* Spooling stands for Simultaneous Peripheral Operations On-Line. It is an I/O management technique where data intended for a slow peripheral (like a printer) is temporarily held in a high-speed buffer/queue so that faster CPU processes are not blocked waiting for the device.

**Q2: How does this project achieve Mutual Exclusion?**
> *Answer:* We use Java's `ReentrantLock` named `printerLock`. Only the thread that successfully acquires `printerLock.lock()` can access the virtual printer. All other threads attempting to access the printer are suspended until `printerLock.unlock()` is executed in a `finally` block.

**Q3: How does your implementation prevent busy waiting?**
> *Answer:* Instead of running a `while(queue.isEmpty())` loop that burns CPU cycles, the consumer thread invokes `jobAvailable.await()`, releasing the lock and putting the thread in a waiting state. When a producer thread enqueues a job, it calls `jobAvailable.signal()`, waking up the consumer.

**Q4: Why did you use `ReentrantLock` instead of the `synchronized` keyword?**
> *Answer:* `ReentrantLock` provides advanced capabilities: fair queuing (`new ReentrantLock(true)`) to prevent starvation, multiple condition variables, timed lock attempts, and programmatic inspection of lock state (`isLocked()`, `getQueueLength()`), which powers the live **Synchronization Monitor** telemetry in our frontend.
