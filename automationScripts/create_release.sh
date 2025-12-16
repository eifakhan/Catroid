#!/bin/bash
set -euo pipefail

if [ $# -ne 4 ]; then
  echo "Usage: $0 <github-repo> <token> <artifact-path> <release-version>"
  exit 1
fi

REPO="$1"
TOKEN="$2"
ARTIFACT="$3"
VERSION="$4"

RELEASES_URL="https://api.github.com/repos/$REPO/releases"
UPLOAD_BASE_URL="https://uploads.github.com/repos/$REPO/releases"

command -v jq >/dev/null 2>&1 || {
  echo "jq is required but not installed"
  exit 1
}

if [ ! -s "$ARTIFACT" ]; then
  echo "Artifact does not exist or is empty: $ARTIFACT"
  exit 1
fi

echo "Checking for existing release: $VERSION"

RELEASE_ID=$(curl -s \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "$RELEASES_URL/tags/$VERSION" \
  | jq -r '.id // empty')

if [ -z "$RELEASE_ID" ]; then
  echo "Release not found. Creating release $VERSION"

  CREATE_RESPONSE=$(curl -s -X POST \
    -H "Authorization: Bearer $TOKEN" \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "$RELEASES_URL" \
    -d "{
      \"tag_name\": \"$VERSION\",
      \"name\": \"$VERSION\",
      \"body\": \"Release $VERSION\",
      \"draft\": false,
      \"prerelease\": false,
      \"target_commitish\": \"main\"
    }")

  RELEASE_ID=$(echo "$CREATE_RESPONSE" | jq -r '.id')

  if [ -z "$RELEASE_ID" ] || [ "$RELEASE_ID" = "null" ]; then
    echo "Failed to create release"
    echo "$CREATE_RESPONSE"
    exit 1
  fi
fi

echo "Using release ID: $RELEASE_ID"

FILENAME=$(basename "$ARTIFACT")
UPLOAD_URL="$UPLOAD_BASE_URL/$RELEASE_ID/assets?name=$FILENAME"

echo "Uploading asset: $FILENAME"

UPLOAD_RESPONSE=$(curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @"$ARTIFACT" \
  "$UPLOAD_URL")

if echo "$UPLOAD_RESPONSE" | jq -e '.browser_download_url' >/dev/null; then
  echo "Artifact uploaded successfully"
else
  echo "Upload failed"
  echo "$UPLOAD_RESPONSE"
  exit 1
fi
