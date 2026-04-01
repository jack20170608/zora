#!/bin/bash

set -e

# Check if commit message is provided
if [ $# -eq 0 ]; then
    echo "Error: Commit message is required"
    echo "Usage: ./commit.sh \"your commit message\""
    exit 1
fi

COMMIT_MESSAGE="$1"

# Add all changes
echo "Adding all changes..."
git add .

# Commit
echo "Committing..."
git commit -m "$COMMIT_MESSAGE"

# Push to remote
echo "Pushing to remote..."
git push

echo "Done! ✅"
