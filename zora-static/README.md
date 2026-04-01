# zora-static

Static Resources Aggregator module for the zora project.

## Description

This is a pom-type aggregator module that provides structure for organizing static resources used throughout the zora project. Static resources may include:

- SQL migration scripts
- Configuration file templates
- JSON schema files
- HTML/CSS/JS assets
- Other static content

## Module Structure

```
zora-static/
├── pom.xml
└── src/
    ├── main/
    │   └── resources/          # Main static resources
    └── test/
        └── resources/          # Test static resources
```

## Usage

Add submodules here when you need to organize different categories of static resources.
