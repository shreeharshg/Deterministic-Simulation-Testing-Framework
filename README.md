# FoundationTest: Deterministic Distributed Systems Sandbox

![Java](https://img.shields.io/badge/Java_21-Project_Loom-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Next.js](https://img.shields.io/badge/Next.js_14-React_Flow-000000?style=for-the-badge&logo=nextdotjs&logoColor=white)
![LMAX](https://img.shields.io/badge/LMAX_Disruptor-Lock--Free-blue?style=for-the-badge)
![Testing](https://img.shields.io/badge/JUnit_5-Property_Testing-25A162?style=for-the-badge&logo=junit5&logoColor=white)

A testing framework built in **Java 21** to simulate network failures and 100% reliably reproduce race conditions in a distributed financial ledger. 

![Dashboard Preview](dashboard.png)

## 📌 Project Overview
Testing distributed consensus algorithms is notoriously difficult because real-world network latency and OS thread scheduling are unpredictable, leading to "Heisenbugs" (bugs that disappear when you try to debug them). 

This project solves that by running a mock banking cluster inside a **single-threaded, virtual-time event loop**. By using a seeded Random Number Generator to inject network packet loss, every transaction failure becomes perfectly deterministic. If a bug happens on Seed `42`, inputting `42` again will reproduce the exact same network failures down to the microsecond.

## 🧠 Core Engineering Concepts

* **The Deterministic Engine:** Bypasses the OS thread scheduler using a `PriorityQueue` in Core Java 21. Virtual time only moves forward when events process, guaranteeing exact reproducibility.
* **Chaos Network & 2PC Ledger:** Nodes communicate via strict binary **Protobufs**. The mock network deterministically drops and delays packets to intentionally break a **Two-Phase Commit (2PC)** algorithm.
* **Cryptographic Persistence (WAL):** Nodes simulate database crash recovery using a custom Write-Ahead Log. It is backed by **Java NIO** memory-mapped files and chained with SHA-256 cryptographic hashes to prevent tampering.
* **Mass Fuzzing (Project Loom):** Leverages Java 21 **Virtual Threads** to spin up 10,000 isolated simulation universes concurrently. It processes over 500,000 transactions in seconds to hunt for edge-case failures.
* **Lock-Free Telemetry:** Integrates the **LMAX Disruptor**. By utilizing a lock-free ring buffer with Cache-Line padding to prevent false sharing, the engine offloads metrics to a WebSocket server without bottlenecking the CPU.
* **Automated Verification:** Validated by **JUnit 5** property-based tests that mathematically assert strict ACID invariants (e.g., verifying the conservation of money across the cluster after random network chaos).
* **Interactive Dashboard:** A **Next.js & React Flow** frontend that allows users to trigger the fuzzer, inspect live ledger balances, and visually playback the exact packet drops of failing seeds.
