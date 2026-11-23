"""Arma los 3 escenarios recalculados para test_zoom. Uso: python zoom_casos.py plantilla.xlsx prefijo"""
import sys, random, datetime as dt
from harness import build_case
T, PRE = sys.argv[1], sys.argv[2]
build_case(T, f"{PRE}_aprende.xlsx", [], [], [], {}, modo_aprendizaje="Sí")
build_case(T, f"{PRE}_vacio.xlsx", [], [], [], {})
NOM = ["María José Fuentealba", "Juan Pablo Riquelme", "Francisca Valenzuela", "Cristóbal Henríquez", "Josefina Altamirano",
       "Maximiliano Sepúlveda", "Constanza Barrientos", "Sebastián Covarrubias", "Antonia Bustamante", "Bartolomé Echeverría",
       "Ana", "Luis"]
rnd = random.Random(5); ini = dt.date(2025, 1, 6); auto = []; deliv = []
for k in range(514):
    f = ini + dt.timedelta(days=k); a1 = rnd.randint(80, 160); s1 = rnd.randint(150, 330); a2 = rnd.randint(40, 140); s2 = rnd.randint(150, 330)
    sd = round((a1 * s1 + a2 * s2) / (a1 + a2))
    auto += [(f, "AM", *rnd.sample(NOM, 3), a1, s1 // 60, s1 % 60), (f, "PM", *rnd.sample(NOM, 3), a1 + a2, sd // 60, sd % 60)]
    if k > 400: deliv += [(f, t, f"https://drive.google.com/file/d/{rnd.getrandbits(64):x}/view", "Sí" if rnd.random() < .8 else "No") for t in ("AM", "PM")]
auto.append((ini, "AM", "Nombre Que No Está", "Ana", "Luis", 100, 3, 10))           # provoca avisos
auto.append((ini + dt.timedelta(days=3), "AM", *auto[6][2:]))                       # fila repetida
build_case(T, f"{PRE}_lleno.xlsx", auto, deliv, NOM, {"L7": 3})
print("ok")
