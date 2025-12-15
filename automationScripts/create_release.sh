#!/bin/bash

# Check if all necessary parameters are provided
if [ $# -ne 4 ]; then
  echo "Usage: $0 <github-repo> <token> <artifact-path> <release-version>"
  exit 1
fi

# Assign parameters to variables
REPO=$1
TOKEN=$2
ARTIFACT=$3
VERSION=$4

# GitHub API URLs
RELEASES_URL="https://api.github.com/repos/$REPO/releases"
UPLOAD_URL="https://uploads.github.com/repos/$REPO/releases"

echo "Parameters: "
echo "Release url: $RELEASES_URL"
echo "Upload url: $UPLOAD_URL"
echo "Artifact: $ARTIFACT"
echo "Version: $VERSION"
echo "Token: $TOKEN"

if [ ! -s $ARTIFACT ]; then
    echo "File does not exist or is empty"
    exit 1
fi

echo 'curl -s -H "Authorization: token $TOKEN" "$RELEASES_URL/tags/$VERSION"'
# Step 1: Check if the release exists
RELEASE_ID=$(curl -s -H "Authorization: bearer $TOKEN" "$RELEASES_URL/tags/$VERSION" | jq -r '.id')
if [ "$RELEASE_ID" == "null" ]; then
  echo "Release $VERSION does not exist. Creating a new release."
  # Step 2: Create the release
  CREATE_RESPONSE=$(curl -L -X POST -H "Accept: application/vnd.github+json" -H "Authorization: token $TOKEN" -H "X-GitHub-Api-Version: 2022-11-28" \
  $RELEASES_URL\
    -d '{
      "tag_name": "'"$VERSION"'",
      "name": "'"$VERSION"'",
      "body": "Release '"$VERSION"'",
      "draft": false,
      "prerelease": false,
	  "target_commitish":"main",
	  "generate_release_notes":false
    }')
  RELEASE_ID=$(echo $CREATE_RESPONSE | jq -r '.id')

  if [ "$RELEASE_ID" == "null" ]; then
    echo "Failed to create the release."
    exit 1
  fi
fi

echo "Obtained release id: $RELEASE_ID"

# Step 3: Upload the artifact to the release
FILENAME=$(basename $ARTIFACT)
UPLOAD_URL="$UPLOAD_URL/$RELEASE_ID/assets?name=$FILENAME"

UPLOAD_RESPONSE=$(curl -s -X POST -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @"$ARTIFACT" \
  "$UPLOAD_URL")

if echo "$UPLOAD_RESPONSE" | grep -q "browser_download_url"; then
  echo "Artifact uploaded successfully!"
else
  echo "Failed to upload the artifact."
  echo "Response: $UPLOAD_RESPONSE"
  exit 1
fi
