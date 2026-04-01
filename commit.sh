#!/bin/bash

set -e

# Check if commit message is provided
if [ $# -eq 0 ]; then
    echo "Error: Commit message is required"
    echo "Usage: ./commit.sh \"your commit message\" [remote-name]"
    exit 1
fi

COMMIT_MESSAGE="$1"
TARGET_REMOTE="$2"

# Add all changes
echo "Adding all changes..."
git add .

# Commit
echo "Committing..."
git commit -m "$COMMIT_MESSAGE"

# Get list of remotes
REMOTES=$(git remote)
REMOTE_COUNT=$(echo "$REMOTES" | wc -l | tr -d '[:space:]')

echo "Found $REMOTE_COUNT remote(s):"
echo "$REMOTES"
echo

# Determine which remote to push to
if [ -n "$TARGET_REMOTE" ]; then
    # Remote specified in command line
    SELECTED_REMOTE="$TARGET_REMOTE"
    echo "Using specified remote: $SELECTED_REMOTE"
elif [ "$REMOTE_COUNT" -eq 1 ]; then
    # Only one remote, use it
    SELECTED_REMOTE="$REMOTES"
    echo "Only one remote available, using: $SELECTED_REMOTE"
else
    # Multiple remotes, let user choose
    echo "Multiple remotes detected. Please select which remote to push to:"
    echo
    select SELECTED_REMOTE in $REMOTES; do
        if [ -n "$SELECTED_REMOTE" ]; then
            echo "Selected: $SELECTED_REMOTE"
            break
        else
            echo "Invalid selection, please try again."
        fi
    done
fi

echo

# Push to selected remote
echo "Pushing to $SELECTED_REMOTE..."
git push "$SELECTED_REMOTE"

echo
echo "Done! ✅"
