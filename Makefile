# Build and install Safanoria for this OS: the CLI (safanoria-cli) and the desktop app (safanoria).
#   make install                   # to ~/.local/bin (the app's files in ~/.local/share/safanoria)
#   make install PREFIX=/usr/local # to /usr/local/bin (may need sudo)
#   make install-cli               # only the CLI

PREFIX ?= $(HOME)/.local
BINDIR := $(PREFIX)/bin
APPDIR := $(PREFIX)/share/safanoria

ifeq ($(OS),Windows_NT)
  TARGET := MingwX64
  DIR := mingwX64
  EXE := safanoria-cli.exe
  NAME := safanoria-cli.exe
else ifeq ($(shell uname -s),Darwin)
  TARGET := MacosArm64
  DIR := macosArm64
  EXE := safanoria-cli.kexe
  NAME := safanoria-cli
  APP := safanoria.app
  APP_EXE := safanoria.app/Contents/MacOS/safanoria
else
  TARGET := LinuxX64
  DIR := linuxX64
  EXE := safanoria-cli.kexe
  NAME := safanoria-cli
  APP := safanoria
  APP_EXE := safanoria/bin/safanoria
endif

BINARY := cli/build/bin/$(DIR)/releaseExecutable/$(EXE)
APP_BUILD := gui/build/compose/binaries/main/app

.PHONY: build build-app install install-cli install-app uninstall test

build:
	./gradlew :cli:linkReleaseExecutable$(TARGET)

build-app:
	./gradlew :gui:createDistributable

install: install-cli install-app

install-cli: build
	mkdir -p "$(BINDIR)"
	cp "$(BINARY)" "$(BINDIR)/$(NAME)"
	@echo "installed $(BINDIR)/$(NAME): $$("$(BINDIR)/$(NAME)" version)"

# The app with its Java runtime goes to APPDIR; BINDIR gets a launcher for it, as install.sh
# does (it replaces the CLI of 0.2 and earlier, which had that name).
ifdef APP
install-app: build-app
	mkdir -p "$(BINDIR)" "$(APPDIR)"
	rm -rf "$(APPDIR)/$(APP)"
	cp -R "$(APP_BUILD)/$(APP)" "$(APPDIR)/$(APP)"
	rm -f "$(BINDIR)/safanoria"
	printf '%s\n' '#!/bin/sh' \
	  '# safanoria app launcher (make install): the desktop app, on the project of the working directory.' \
	  '[ $$# -eq 0 ] && set -- "$$PWD"' \
	  'exec "$(APPDIR)/$(APP_EXE)" "$$@"' > "$(BINDIR)/safanoria"
	chmod 755 "$(BINDIR)/safanoria"
	@echo "installed $(BINDIR)/safanoria: $$("$(BINDIR)/safanoria" --version)"
else
install-app:
	@echo "the app is installed by install.ps1 on Windows; from a checkout: gradlew :gui:run"
endif

uninstall:
	rm -f "$(BINDIR)/$(NAME)" "$(BINDIR)/safanoria"
	rm -rf "$(APPDIR)/safanoria" "$(APPDIR)/safanoria.app"

test:
	./gradlew allTests
