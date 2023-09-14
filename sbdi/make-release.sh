#!/bin/bash

# Get the last GitHub tag version
last_tag=$(git describe --tags --abbrev=0)

major=$(echo "$last_tag" | cut -d. -f1)
minor=$(echo "$last_tag" | cut -d. -f2)
patch=$(echo "$last_tag" | cut -d. -f3)

new_patch=$((patch + 1))

echo
read -p "Current version: $last_tag. Enter the new version (or press Enter for $major.$minor.$new_patch): " new_version_input

if [ -z "$new_version_input" ]; then
    new_version="$major.$minor.$new_patch"
else
    new_version="$new_version_input"
fi

# Validate the new version format (assuming it follows semantic versioning)
if ! [[ "$new_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Invalid version format. Please use semantic versioning (e.g., 1.2.3)."
    exit 1
fi

echo "Updating to version $new_version"

# Create a new tag
git tag "$new_version"
git push origin "$new_version"

echo "Tag $new_version created and pushed."
