# AI Autopilot — Gemini CLI / Termux

Sterowanie:
- `megane-ai auth` — jednorazowa autoryzacja Gemini
- `megane-ai on` — start ciągłej pracy
- `megane-ai off` — zatrzymanie
- `megane-ai status` — status procesu + branch
- `megane-ai log` — ostatnie 120 linii logu
- `megane-ai once` — jedna iteracja
- `megane-ai doctor` — szybka diagnostyka

## Model pracy

Każda iteracja:
1. wymaga czystego working tree,
2. czyta aktualny kod + pliki źródła prawdy,
3. realizuje dokładnie jeden bezpieczny checkpoint,
4. wykonuje testy,
5. przegląda diff,
6. tworzy lokalny commit,
7. kończy iterację.

Pętla uruchamia następną iterację tylko po czystym zakończeniu poprzedniej.

## Bezpieczniki

- tryb Gemini: `auto_edit`, nie `yolo`,
- shell działa według `policy.toml`,
- automatyczny push/merge/rebase/reset/clean są zablokowane,
- sieć i ADB są zablokowane w unattended loop,
- sekrety i credential files są poza zakresem,
- każdy stan niepewny kończy iterację jako JOB STOP,
- Renault WRITE/RESET/CONFIGURATION/ACTUATOR pozostaje LOCKED,
- testy jednostkowe nie są traktowane jako dowód działania na fizycznym aucie.

## Termux

Remote Desktop Commander nie eksportuje `TERMUX_VERSION`, dlatego launcher ustawia tę zmienną tylko dla procesu Gemini CLI. Nie modyfikuje globalnego środowiska Androida.

Logi lokalne:
`~/.megane-ai/logs/`

Sterownik:
`~/bin/megane-ai` -> symlink do wersjonowanego pliku w repo.
