# VerseWorks

VerseWorks is a NeoForge mod for Minecraft 1.21.1.

This repository is the working source for the mod. The current build is early and experimental. Expect rough edges, placeholder assets, and systems that may change without much ceremony between iterations.

## Requirements

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.x

## Local setup

The template includes a setup script that prepares the local Python environment and checks the basic toolchain.

```powershell
./setup_template.ps1
```

After setup, the main run targets are the VS Code launch entries already included in the workspace, such as `runClient`, `runServer`, and `runData`.

## Project layout

- `src/main/java` contains the mod code.
- `src/main/resources` contains runtime assets and data.
- `src/generated/resources` contains generated assets and data.

## License

This repository uses the PolyForm Noncommercial 1.0.0 license.

That means people may use, modify, and redistribute the code for noncommercial purposes, but they may not sell it or use it as part of a commercial offering without separate permission.

See `LICENSE.txt` for the repository license text.