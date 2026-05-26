FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY simulate.py .

# Container stays alive so you can exec into it or run the demo manually
CMD ["tail", "-f", "/dev/null"]
