#!/usr/bin/env python3
"""Synchronize the source feed's digest to the actual GitHub Release asset.

Semantic release creates release metadata before GitHub uploads the final .mpp
artifact. In practice these digests can differ. This runs after publishing
and updates only releases/latest.json, using the Contents API's optimistic
concurrency SHA. Never rewrites patches, tags, assets or other release files.
"""
import base64
import json
import os
import urllib.request
import urllib.error
import time

repo=os.environ["GITHUB_REPOSITORY"]
tag=os.environ["PUBLISHED_TAG"]
token=os.environ["GH_TOKEN"]
api="https://api.github.com/repos/"+repo

def call(url,method="GET",data=None):
    payload=None if data is None else json.dumps(data).encode()
    req=urllib.request.Request(url,data=payload,method=method,headers={
        "Authorization":"Bearer "+token,
        "Accept":"application/vnd.github+json",
        "X-GitHub-Api-Version":"2022-11-28",
        "User-Agent":"Morphe-release-digest-reconciler",
        "Content-Type":"application/json"
    })
    with urllib.request.urlopen(req,timeout=25) as response:
        return json.load(response)

release=None
for attempt in range(6):
    try:
        release=call(api+"/releases/tags/"+tag)
        break
    except urllib.error.HTTPError as e:
        if e.code!=404 or attempt==5:raise
        time.sleep(3)
if release is None: raise SystemExit("Published GitHub Release unavailable")
version=tag.removeprefix("v")
asset="patches-"+version+".mpp"
matching=[x for x in release.get("assets",[]) if x.get("name")==asset]
if len(matching)!=1:raise SystemExit("Published release has no unambiguous "+asset)
digest=matching[0].get("digest","")
if not digest.startswith("sha256:") or len(digest)!=71:
    raise SystemExit("GitHub did not provide a valid published asset digest")
sha256=digest.split(":",1)[1].lower()
path="releases/latest.json"
for attempt in range(3):
    entry=call(api+"/contents/"+path+"?ref=main")
    source=json.loads(base64.b64decode(entry["content"]).decode())
    if source.get("version")!=version or source.get("asset")!=asset:
        raise SystemExit("Source feed version mismatches published release tag")
    if source.get("sha256")==sha256:
        print("PASS: published release and source SHA-256 are already aligned")
        break
    source["sha256"]=sha256
    data={"message":"chore(release): match v"+version+" published asset digest [skip ci]",
          "content":base64.b64encode((json.dumps(source,indent=2)+"\n").encode()).decode(),
          "sha":entry["sha"],
          "branch":"main"}
    try:
        call(api+"/contents/"+path,method="PUT",data=data)
        print("Updated source feed SHA-256 to verified published release digest",sha256)
        break
    except urllib.error.HTTPError as e:
        if e.code!=409 or attempt==2:raise
        time.sleep(2)
else:raise SystemExit("Could not write published checksum")

fresh=call(api+"/contents/"+path+"?ref=main")
current=json.loads(base64.b64decode(fresh["content"]).decode())
if current.get("sha256")!=sha256:raise SystemExit("Source SHA-256 reconciliation did not persist")
print("PASS: public release asset metadata synchronized",tag,asset,sha256)
