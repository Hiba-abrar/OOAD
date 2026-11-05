@echo off
REM Starts the Streamlit web UI. The Java banking engine starts automatically.
cd /d "%~dp0"
python -m streamlit run ui\app.py
