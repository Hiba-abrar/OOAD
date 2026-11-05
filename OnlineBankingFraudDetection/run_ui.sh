#!/usr/bin/env bash
# Starts the Streamlit web UI. The Java banking engine starts automatically.
cd "$(dirname "$0")"
python3 -m streamlit run ui/app.py
