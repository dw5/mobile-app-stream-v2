#!/bin/bash
#202303
#todo: playlist https://vanillo.tv/playlist/FTWFYu3JS1qpyXEGtdtQ0A https://api.vanillo.tv/v1/playlists/FTWFYu3JS1qpyXEGtdtQ0A/videos

read -p "Enter URL: " url

# Remove the prefixes using sed
keyVideoID=$(echo "$url" | sed -e 's#^https://dev.vanillo.tv/v/##' -e 's#^https://beta.vanillo.tv/v/##' -e 's#^https://vanillo.tv/v/##' -e 's#^https://vanillo.tv/embed/##')

# 1 Video ID double check
echo "Video: $keyVideoID"
# read -p "OK " testYN

# OPTIONAL: 2 video info https://api.vanillo.tv/v1/videos/$keyVideoID
videoinfo_url="https://api.vanillo.tv/v1/videos/$keyVideoID"

videoinfo_response=$(curl --silent "$videoinfo_url")
echo $videoinfo_response>$keyVideoID.json
videoinfo_title=$(echo "$videoinfo_response" | jq -r '.data.title')
videoinfo_desc=$(echo "$videoinfo_response" | jq -r '.data.description')
videoinfo_thumbnail=$(echo "$videoinfo_response" | jq -r '.data.thumbnail')
wget $videoinfo_thumbnail

# 3 get watch token
response=$(curl --silent --header "Content-Type: application/json" --request POST --data "{\"videoId\":\"$keyVideoID\"}" https://api.vanillo.tv/v1/_/watch)
watchToken=$(echo "$response" | jq -r '.data.watchToken')
echo "Watch token: $watchToken"

# 4 playback links
manifests_url="https://api.vanillo.tv/v1/_/watch/manifests?watchToken=$watchToken"

manifests_response=$(curl --silent "$manifests_url")

dash_url=$(echo "$manifests_response" | jq -r '.data.media.dash')
hls_url=$(echo "$manifests_response" | jq -r '.data.media.hls')

# 5 print hls and dash values and use 'em
echo "HLS URL: $hls_url"
echo "DASH URL: $dash_url"

read -p "[Y/y] for Download, otherwise will play via MPV: " wut2do
  if [[ $wut2do == "Y" ]] || [[ $wut2do == "y" ]]; then
  
    yt-dlp  "$hls_url" -o "csmcVDL-$keyVideoID-$(date +"%Y-%m-%d_%T")" --referer "https://vanillo.tv"
	#yt-dlp -F - "$dash_url" -o "csmcVDL-$(date +"%Y-%m-%d_%T")" --referer "https://vanillo.tv"
	
else
	mpv "$hls_url" --referrer=https://vanillo.tv
    #mpv "$dash_url" --referrer=https://vanillo.tv
fi

echo "done"
echo -e "\n\nThank you for your purchase! erm... Download for offline Archive purposes. \nPlease come back again."
