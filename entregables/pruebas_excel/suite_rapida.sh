T=${1:-comp_v.xlsx}
for t in test_verdad test_errores test_e2e test_torpe test_uno test_aprendizaje test_aprendizaje_e2e test_vida_real test_horarios test_clave; do
  echo "=== $t"; timeout 3000 python3 $t.py $T 2>&1 | grep -E "^[A-ZÁÉÍÓÚ ].*[0-9]+ ?/ ?[0-9]+|fallos|✗|Error|Traceback" | head -15
done
echo "=== auditoria"; python3 auditoria_visible.py $T 2>&1 | tail -5
echo "=== ciclos"; python3 ciclos.py $T 2>&1 | tail -3
echo "=== bench"; python3 bench.py $T final 2>&1 | tail -5
echo FIN
