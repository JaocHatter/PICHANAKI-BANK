# Dockerfile
FROM python:3.9-slim

WORKDIR /app

COPY src/worker_node_python.py .
# If you have other local dependencies or data initialization scripts, copy them too.
# COPY init_data.sh . 
# RUN chmod +x init_data.sh

# Create a directory for data files if your script doesn't create it.
# The actual data files will be mounted via Kubernetes volumes based on ConfigMaps or PersistentVolumes.
RUN mkdir -p /app/data 

EXPOSE 8082

# Environment variables for data file names will be set in Kubernetes deployment
# ENV CUENTA_FILENAME="cuenta_default.txt"
# ENV TRANSACCIONES_FILENAME="transacciones_default.txt"

# CMD ["./init_data.sh"] # If you have an init script for data
CMD ["python", "worker_node_python.py"]