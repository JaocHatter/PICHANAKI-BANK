#!/bin/bash

# Aplicar todos los ConfigMaps del 0 al 5
for i in {0..5}; do
  echo "Aplicando cm-worker-db-$i-init.yaml..."
  minikube kubectl -- apply -f "cm-worker-db-$i-init.yaml"
done

echo "¡Todos los ConfigMaps se han aplicado correctamente!"
