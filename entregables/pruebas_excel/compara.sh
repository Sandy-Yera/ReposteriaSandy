for m in limpio completo; do for s in 1 2 3 4 5; do
  for v in viejo_v12 comp_v; do
    if [ $v = comp_v ] && [ $s -le 3 ]; then continue; fi
    echo "=== $v $m $s"; python3 test_sesgos.py $v.xlsx $s $m 2>&1 | grep -E "Spearman ajustado|cobertura|P04|P05|SESGOS|✗|cajeros \(justo|Traceback|Error"
  done
done; done
echo FIN
