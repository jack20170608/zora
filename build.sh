#!/bin/bash
# Zora Framework Build Script
# Description: Build script for the Zora Java multi-module project

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Project information
PROJECT_NAME="zora"
MVN_CMD="mvn"



# Function to print info message
info() {
    echo -e "${GREEN}[INFO] $*${NC}"
}

# Function to print warning message
warn() {
    echo -e "${YELLOW}[WARNING] $*${NC}"
}

# Function to print error message
error() {
    echo -e "${RED}[ERROR] $*${NC}"
    exit 1
}

# Function to check if Maven is installed
check_maven() {
    if ! command -v mvn &> /dev/null; then
        error "Maven (mvn) is not installed or not in PATH"
    fi
    info "Maven is installed: $(mvn -v | head -n 1)"
}

# Function to clean the project
clean() {
    info "Cleaning project..."
    $MVN_CMD clean -Drevision=$VERSION
    info "Clean completed"
}

# Function to compile the project
compile() {
    info "Compiling project..."
    $MVN_CMD compile -Drevision=$VERSION
    info "Compilation completed"
}

# Function to run tests
test() {
    info "Running tests..."
    $MVN_CMD test -Drevision=$VERSION
    info "Tests completed"
}

# Function to package the project
package() {
    info "Packaging project..."
    $MVN_CMD package -Drevision=$VERSION -DskipTests
    info "Packaging completed"
}

# Function to install to local repository
install() {
    info "Installing to local Maven repository..."
    $MVN_CMD install -Drevision=$VERSION -DskipTests
    info "Installation completed"
}

# Function to clean and build everything
full_build() {
    info "Starting full build of $PROJECT_NAME..."
    check_maven
    clean
    compile
    test
    package
    install
    info "Full build completed successfully!"
}

# Function to build without running tests
quick_build() {
    info "Starting quick build (skipping tests)..."
    check_maven
    clean
    compile
    package
    install
    info "Quick build completed successfully!"
}

# Function to show help
show_help() {
    echo "Zora Framework Build Script"
    echo
    echo "Usage: ./build.sh [COMMAND]"
    echo
    echo "Commands:"
    echo "  clean       Clean the project (remove target directories)"
    echo "  compile     Compile all modules"
    echo "  test        Run all tests"
    echo "  package     Package all modules (skips tests)"
    echo "  install     Install to local Maven repository (skips tests)"
    echo "  build       Full clean build with tests and install"
    echo "  quick       Quick build (skips tests)"
    echo "  help        Show this help message"
    echo
    echo "Examples:"
    echo "  ./build.sh build     # Full build"
    echo "  ./build.sh clean     # Only clean"
    echo "  ./build.sh quick     # Build without tests"
}

# Read version from VERSION file
if [ -f "VERSION" ]; then
    VERSION=$(tr -d '[:space:]' < VERSION)
else
    error "VERSION file not found in project root"
fi
info "Project version: $VERSION"

# Main script
main() {
    case "$1" in
        clean)
            clean
            ;;
        compile)
            compile
            ;;
        test)
            check_maven
            test
            ;;
        package)
            check_maven
            package
            ;;
        install)
            check_maven
            install
            ;;
        build)
            full_build
            ;;
        quick)
            quick_build
            ;;
        help)
            show_help
            ;;
        "")
            full_build
            ;;
        *)
            error "Unknown command: $1. Use 'help' to see available commands."
            ;;
    esac
}

main "$@"
