# Projektregeln: Dokumentation & Releases

1. **`README.md` immer aktuell halten:**
   - Bei jeder funktionalen Erweiterung, Architekturänderung, neuen Screens/Modulen, geänderten SDK-Versionen oder neuen Build-/Test-/Release-Skripten muss `README.md` im Projekt-Root **direkt und unaufgefordert** auf den aktuellen Stand gebracht werden.
   - Keine veralteten SDK-Angaben, fehlenden Module oder unvollständigen Script-Beschreibungen stehen lassen.

2. **Releases nur mit aussagekräftigen Release-Notes:**
   - Releases in Google Play dürfen **niemals** ohne aussagekräftige deutsche Release-Notes veröffentlicht werden (`./scripts/publish_internal.sh "<Release-Notes>"`).

3. **Play-Store-Signatur auf Testgeräten schützen:**
   - Der `debug`-Build nutzt `applicationIdSuffix = ".debug"`, damit die Play-Store-Installation (`com.verhuelsdonk.navigator`) für physische Auto-Tests unberührt bleibt.
