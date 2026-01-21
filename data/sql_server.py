#!/usr/bin/env python3
import socket
import sys
import threading
import sqlite3
import os

SERVER_NAME = "STOMP_PYTHON_SQL_SERVER"
DB_FILE = "stomp_server.db"

def recv_null_terminated(sock: socket.socket) -> str:
    data = b""
    while True:
        chunk = sock.recv(1024)
        if not chunk:
            return ""
        data += chunk
        if b"\0" in data:
            msg, _ = data.split(b"\0", 1)
            return msg.decode("utf-8", errors="replace")

def init_database():
    """Initializes the database schema matching Database.java expectations."""
    with sqlite3.connect(DB_FILE) as conn:
        c = conn.cursor()
        
        # Table: users
        c.execute('''CREATE TABLE IF NOT EXISTS users (
                        username TEXT PRIMARY KEY,
                        password TEXT NOT NULL,
                        registration_date DATETIME
                     )''')
        
        # Table: login_history
        c.execute('''CREATE TABLE IF NOT EXISTS login_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL,
                        login_time DATETIME,
                        logout_time DATETIME,
                        FOREIGN KEY(username) REFERENCES users(username)
                     )''')
        
        # Table: file_tracking
        c.execute('''CREATE TABLE IF NOT EXISTS file_tracking (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL,
                        filename TEXT NOT NULL,
                        upload_time DATETIME,
                        game_channel TEXT,
                        FOREIGN KEY(username) REFERENCES users(username)
                     )''')
        
        conn.commit()
    print(f"[{SERVER_NAME}] Database initialized at {DB_FILE}")

def process_sql(sql_command: str) -> str:
    """
    Executes SQL and formats response for Database.java:
    - SELECT: Returns "SUCCESS|row1|row2|..." where row is str(tuple)
    - INSERT/UPDATE: Returns "SUCCESS"
    """
    sql_stripped = sql_command.strip().upper()
    try:
        with sqlite3.connect(DB_FILE) as conn:
            c = conn.cursor()
            c.execute(sql_command)
            
            if sql_stripped.startswith("SELECT"):
                rows = c.fetchall()
                response_data = "|".join([str(row) for row in rows])
                return f"SUCCESS|{response_data}"
            else:
                conn.commit()
                return "SUCCESS"
                
    except Exception as e:
        print(f"SQL Error: {e}")
        return f"ERROR:{str(e)}"

def handle_client(client_socket: socket.socket, addr):
    print(f"[{SERVER_NAME}] Client connected from {addr}")
    try:
        while True:
            message = recv_null_terminated(client_socket)
            if message == "":
                break

            response = process_sql(message)
            
            response_bytes = (response + "\0").encode("utf-8")
            client_socket.sendall(response_bytes)

    except Exception as e:
        print(f"[{SERVER_NAME}] Error handling client {addr}: {e}")
    finally:
        try:
            client_socket.close()
        except Exception:
            pass

def start_server(host="127.0.0.1", port=7778):
    init_database()
    
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    try:
        server_socket.bind((host, port))
        server_socket.listen(5)
        print(f"[{SERVER_NAME}] Server started on {host}:{port}")
        
        while True:
            client_socket, addr = server_socket.accept()
            t = threading.Thread(
                target=handle_client,
                args=(client_socket, addr),
                daemon=True
            )
            t.start()

    except KeyboardInterrupt:
        print(f"\n[{SERVER_NAME}] Shutting down server...")
    finally:
        try:
            server_socket.close()
        except Exception:
            pass

if __name__ == "__main__":
    start_server(port=7778)