# Simpl: Compilador en Java

Aquest projecte implementa un compilador de llenguatge Simpl a codi MIPS, escrit en Java. El compilador tradueix fitxers amb extensió `.smpl` a instruccions MIPS que es poden executar en l'entorn MARS.

---

## Requisits previs

Abans d’executar el compilador, assegura’t de tenir instal·lat:

- **Java SE Development Kit (JDK)** 11 o superior
- **IDE** (opcional): IntelliJ IDEA, Eclipse, VSCode, etc.
- **MARS** per executar el codi MIPS generat

---

## Ús del compilador
Per executar el compilador, segueix aquests passos:

    1. Col·loca el teu fitxer .smpl dins de src/Files/Codes.

    2. Obre Main.java i modifica la constant FILE_PATH:

        FILE_PATH = "src/Files/Codes/example.smpl";

    substituint example.smpl pel nom del teu fitxer.

    3. Des de l’IDE, executa la classe Main
    
    4. Un cop finalitzi l’execució, trobaràs el fitxer program.asm a la carpeta out/ amb el codi MIPS generat.

