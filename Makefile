# Build and install the safanoria CLI for this OS.
#   make install                   # to ~/.local/bin/safanoria
#   make install PREFIX=/usr/local # to /usr/local/bin/safanoria (may need sudo)

PREFIX ?= $(HOME)/.local
BINDIR := $(PREFIX)/bin

ifeq ($(OS),Windows_NT)
  TARGET := MingwX64
  DIR := mingwX64
  EXE := safanoria.exe
  NAME := safanoria.exe
else ifeq ($(shell uname -s),Darwin)
  TARGET := MacosArm64
  DIR := macosArm64
  EXE := safanoria.kexe
  NAME := safanoria
else
  TARGET := LinuxX64
  DIR := linuxX64
  EXE := safanoria.kexe
  NAME := safanoria
endif

BINARY := cli/build/bin/$(DIR)/releaseExecutable/$(EXE)

.PHONY: build install uninstall test

build:
	./gradlew :cli:linkReleaseExecutable$(TARGET)

install: build
	mkdir -p "$(BINDIR)"
	cp "$(BINARY)" "$(BINDIR)/$(NAME)"
	@echo "installed $(BINDIR)/$(NAME): $$("$(BINDIR)/$(NAME)" version)"

uninstall:
	rm -f "$(BINDIR)/$(NAME)"

test:
	./gradlew allTests
