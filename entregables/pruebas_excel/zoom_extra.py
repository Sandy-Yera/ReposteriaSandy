"""Escenario de control para test_zoom (datos que el ajuste no usó). Uso: python zoom_extra.py plantilla.xlsx salida.xlsx"""
import sys, random, datetime as dt
from harness import build_case
NOM = ["Valentina Sotomayor", "Ignacio Larraín Vial", "Florencia Undurraga", "Agustín Montecinos", "Trinidad Errázuriz",
       "Benjamín Cifuentes", "Catalina Ossandón", "Vicente Aldunate", "Emilia Goycoolea", "Tomás Ibarra"]
rnd = random.Random(77); ini = dt.date(2025, 3, 3); auto = []
for k in range(300):
    if 100 <= k < 130: continue                      # un mes sin datos
    f = ini + dt.timedelta(days=k); a1 = rnd.randint(20, 190); s1 = rnd.randint(100, 590); a2 = rnd.randint(5, 160); s2 = rnd.randint(100, 590)
    sd = round((a1 * s1 + a2 * s2) / (a1 + a2))
    auto += [(f, "AM", *rnd.sample(NOM, 3), a1, s1 // 60, s1 % 60), (f, "PM", *rnd.sample(NOM, 3), a1 + a2, sd // 60, sd % 60)]
build_case(sys.argv[1], sys.argv[2], auto, [], NOM, {"F7": "PM", "J7": "Ignacio Larraín Vial", "L7": 2})
print("ok")
