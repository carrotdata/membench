## Membench: Comprehensive Memory and Performance Benchmarking for Caching Servers

### Introduction
Membench is a pioneering benchmark tool designed to evaluate the memory usage and performance of Memcached-compatible servers. This client application offers a unique insight into how caching servers handle a wide range of data sets, making it an essential tool for developers and system administrators. Key features include:

- **Wide Diversity of Data Sets**: Tests with 10 different data sets, ranging from small tweet messages (76 bytes) to large JSON objects (over 3KB), providing a comprehensive assessment of server performance across various scenarios.
- **Memory Usage Measurement**: The first tool to measure the memory consumption of caching servers, ensuring that memory efficiency is as closely monitored as pure performance.
- **Performance Metrics**: In-depth analysis of server response times and throughput, giving a holistic view of the server’s capabilities.
- **Real-World Relevance**: Data sets reflect real-world usage patterns, making the results highly applicable to actual deployment environments.

By combining memory usage metrics with performance measurements, membench provides a thorough evaluation of caching servers, helping optimize their configuration and usage for diverse applications.

## About data sets

We have compiled 10 different data sets, covering different types of a structured, semi-structured and unstructured text objects. Available datasets are:

- **Amazon Product Reviews**. Random samples of book reviews in JSON format. Format: CSV. Average object size - 528 bytes. Example:
```
10,B00171APVA,A21BT40VZCCYT4,Carol A. Reed,0,0,5,1351209600,Healthy Dog Food,This is a very healthy dog food. Good for their digestion. Also good for small puppies. My dog eats her required amount at every feeding
```
- **Airbnb** property descriptions. Average object size - 1367 bytes. Format: CSV. Example:
```
12422935,4.442651256490317,Apartment,Private room,"{TV,""Wireless Internet"",Heating,""Smoke detector"",""Carbon monoxide detector"",""First aid kit"",""Fire extinguisher"",Essentials,Hangers,""Laptop friendly workspace""}",2,1.0,Real Bed,strict,True,SF,"Beautiful private room overlooking scenic views in San Francisco's upscale Noe Valley neighborhood. You'll have your own bedroom, queen bed, dresser, wardrobe, smart TV, workstation, desk&chair, WiFi, and kitchen. Fresh towels and linens are provided for your convenience. MUNI bus stop and all tech shuttles are around the corner, J-Church subway is 5-minute walk away. Restaurants, bars, and cafes are around the corner. Ideal location for experiencing SF or commuting to Silicon Valley. We'll provide 100% cotton sheets and fresh towels. You'll have access to the full size bathroom as well as the kitchen. We'll also provide high speed business class WiFi internet, as well as HBO and Net""ix on your private TV. Guests will have access to kitchen and bathroom in addition to their private bedroom. We'll interact with you as much as you'd like to make your stay as comfortable as possible. We are available if you need us but will not disturb you otherwise. One of the sunniest neighborhoods in S",2017-08-27,t,t,100%,2017-06-07,t,2017-09-05,37.7531640472884,-122.4295260773271,Comfort Suite San Francisco,Noe Valley,3,100.0,https://a0.muscache.com/im/pictures/82509143-4b21-44eb-a556-e3c1e0afac60.jpg?aki_policy=small,94131,1.0,1.0
```
- **Arxiv**. Large collection of scientific publications meta data from arxiv.org site. Format: JSON. Average object size - 1643 bytes. Example:
```
{"id":"0704.0008","submitter":"Damian Swift","authors":"Damian C. Swift","title":"Numerical solution of shock and ramp compression for general material\n  properties","comments":"Minor corrections","journal-ref":"Journal of Applied Physics, vol 104, 073536 (2008)","doi":"10.1063/1.2975338","report-no":"LA-UR-07-2051, LLNL-JRNL-410358","categories":"cond-mat.mtrl-sci","license":"http://arxiv.org/licenses/nonexclusive-distrib/1.0/","abstract":"  A general formulation was developed to represent material models for\napplications in dynamic loading. Numerical methods were devised to calculate\nresponse to shock and ramp compression, and ramp decompression, generalizing\nprevious solutions for scalar equations of state. The numerical methods were\nfound to be flexible and robust, and matched analytic results to a high\naccuracy. The basic ramp and shock solution methods were coupled to solve for\ncomposite deformation paths, such as shock-induced impacts, and shock\ninteractions with a planar interface between different materials. These\ncalculations capture much of the physics of typical material dynamics\nexperiments, without requiring spatially-resolving simulations. Example\ncalculations were made of loading histories in metals, illustrating the effects\nof plastic work on the temperatures induced in quasi-isentropic and\nshock-release experiments, and the effect of a phase transition.\n","versions":[{"version":"v1","created":"Sat, 31 Mar 2007 04:47:20 GMT"},{"version":"v2","created":"Thu, 10 Apr 2008 08:42:28 GMT"},{"version":"v3","created":"Tue, 1 Jul 2008 18:54:28 GMT"}],"update_date":"2009-02-05","authors_parsed":[["Swift","Damian C.",""]]}
```
- **DBLP**. The DBLP is a citation network dataset. The citation data is extracted from DBLP, ACM, MAG (Microsoft Academic Graph), and other sources. Format: JSON. Average object size - 396 bytes. Example:
```
{ "_id" : { "$oid" : "595c2c48a7986c0872f8ba53" }, "mdate" : "2017-05-25", "author" : [ "Gabriele Moser", "Michaela De Martino", "Sebastiano B. Serpico" ], "ee" : "https://doi.org/10.1109/IGARSS.2013.6723567", "booktitle" : "IGARSS", "title" : "A multiscale contextual approach to change detection in multisensor VHR remote sensing images.", "pages" : "3435-3438", "url" : "db/conf/igarss/igarss2013.html#MoserMS13", "year" : "2013", "type" : "inproceedings", "_key" : "conf::igarss::MoserMS13", "crossref" : [ "conf::igarss::2013" ] }
``` 
- **Github**. Github user (public) profiles. Format: JSON. Average object size - 821 bytes. Example:
```
{"login":"justinkadima","id":5258,"avatar_url":"https://avatars.githubusercontent.com/u/5258?v=3","gravatar_id":"","url":"https://api.github.com/users/justinkadima","html_url":"https://github.com/justinkadima","followers_url":"https://api.github.com/users/justinkadima/followers","following_url":"https://api.github.com/users/justinkadima/following{/other_user}","gists_url":"https://api.github.com/users/justinkadima/gists{/gist_id}","starred_url":"https://api.github.com/users/justinkadima/starred{/owner}{/repo}","subscriptions_url":"https://api.github.com/users/justinkadima/subscriptions","organizations_url":"https://api.github.com/users/justinkadima/orgs","repos_url":"https://api.github.com/users/justinkadima/repos","events_url":"https://api.github.com/users/justinkadima/events{/privacy}","received_events_url":"https://api.github.com/users/justinkadima/received_events","type":"User","site_admin":false}
```
- **Ohio**. Ohio State Education Department Employee salary database (public). Format: CSV. Average object size - 102 byte. Example:
```
"Don Potter","University of Akron","Assistant Lecturer","Social Work",2472.0,2019
```
- **Reddit**. Reddit subreddits data sets. Format: JSON. Average object size - 3044 bytes. Example:
```
{"_meta":{"earliest_comment_at":1134365188,"earliest_post_at":1119552233,"num_comments":14230966,"num_comments_updated_at":1707541748,"num_posts":9120981,"num_posts_updated_at":1707519565},"accept_followers":true,"accounts_active":null,"accounts_active_is_fuzzed":false,"active_user_count":null,"advertiser_category":"","all_original_content":false,"allow_discovery":true,"allow_galleries":false,"allow_images":true,"allow_polls":true,"allow_prediction_contributors":false,"allow_predictions":false,"allow_predictions_tournament":false,"allow_talks":false,"allow_videogifs":true,"allow_videos":true,"allowed_media_in_comments":[],"banner_background_color":"#0dd3bb","banner_background_image":"https://styles.redditmedia.com/t5_6/styles/bannerBackgroundImage_yddlxq1m39r21.jpg?width=4000&s=f91d1be5c5a1ea6e492818ecb8a846ea4978563c","banner_img":"","banner_size":null,"can_assign_link_flair":false,"can_assign_user_flair":false,"collapse_deleted_comments":false,"comment_contribution_settings":{"allowed_media_types":null},"comment_score_hide_mins":0,"community_icon":"https://styles.redditmedia.com/t5_6/styles/communityIcon_a8uzjit9bwr21.png?width=256&s=d28ea66f16da5a6c2ccae0d069cc4d42322d69a9","community_reviewed":true,"created":1137537905,"created_utc":1137537905,"description":"To report a site-wide rule violation to the Reddit Admins, please use our [report forms](https://www.reddit.com/report) or message [/r/reddit.com modmail](https://www.reddit.com/message/compose?to=%2Fr%2Freddit.com).\n\nThis subreddit is [archived and no longer accepting submissions.](https://redditblog.com/2011/10/18/saying-goodbye-to-an-old-friend-and-revising-the-default-subreddits/)","disable_contributor_requests":false,"display_name":"reddit.com","display_name_prefixed":"r/reddit.com","emojis_custom_size":null,"emojis_enabled":false,"free_form_reports":true,"has_menu_widget":false,"header_img":null,"header_size":null,"header_title":"","hide_ads":false,"icon_img":"","icon_size":null,"id":"6","is_crosspostable_subreddit":true,"is_enrolled_in_new_modmail":null,"key_color":"","lang":"en","link_flair_enabled":false,"link_flair_position":"","mobile_banner_image":"","name":"t5_6","notification_level":null,"original_content_tag_enabled":false,"over18":false,"prediction_leaderboard_entry_type":1,"primary_color":"#0079d3","public_description":"The original subreddit, now archived.","public_traffic":false,"quarantine":false,"restrict_commenting":false,"restrict_posting":true,"retrieved_on":1707425156,"should_archive_posts":true,"should_show_media_in_comments_setting":true,"show_media":false,"show_media_preview":true,"spoilers_enabled":true,"submission_type":"any","submit_link_label":"","submit_text":"","submit_text_html":null,"submit_text_label":"","subreddit_type":"archived","subscribers":987905,"suggested_comment_sort":null,"title":"reddit.com","url":"/r/reddit.com/","user_can_flair_in_sr":null,"user_flair_background_color":null,"user_flair_css_class":null,"user_flair_enabled_in_sr":false,"user_flair_position":"right","user_flair_richtext":[],"user_flair_template_id":null,"user_flair_text":null,"user_flair_text_color":null,"user_flair_type":"text","user_has_favorited":false,"user_is_banned":false,"user_is_contributor":false,"user_is_moderator":false,"user_is_muted":false,"user_is_subscriber":false,"user_sr_flair_enabled":null,"user_sr_theme_enabled":true,"videostream_links_count":0,"whitelist_status":"all_ads","wiki_enabled":true,"wls":6}

```
- **Spotify**. Spotify top 40 charts by Country, Week. Format: CSV. Average object size - 904 bytes. Example:
```
0,Chantaje (feat. Maluma),1,2017-01-01,Shakira,https://open.spotify.com/track/6mICuAdrwEjh6Y6lroV2Kg,Argentina,top200,SAME_POSITION,253019.0,6mICuAdrwEjh6Y6lroV2Kg,El Dorado,78.0,195840.0,False,2017-05-26,"['AR', 'AU', 'AT', 'BE', 'BO', 'BR', 'BG', 'CA', 'CL', 'CO', 'CR', 'CY', 'CZ', 'DK', 'DO', 'DE', 'EC', 'EE', 'SV', 'FI', 'FR', 'GR', 'GT', 'HN', 'HK', 'HU', 'IS', 'IE', 'IT', 'LV', 'LT', 'LU', 'MY', 'MT', 'MX', 'NL', 'NZ', 'NI', 'NO', 'PA', 'PY', 'PE', 'PH', 'PL', 'PT', 'SG', 'SK', 'ES', 'SE', 'CH', 'TW', 'TR', 'UY', 'US', 'GB', 'AD', 'LI', 'MC', 'ID', 'JP', 'TH', 'VN', 'RO', 'IL', 'ZA', 'SA', 'AE', 'BH', 'QA', 'OM', 'KW', 'EG', 'MA', 'DZ', 'TN', 'LB', 'JO', 'PS', 'IN', 'BY', 'KZ', 'MD', 'UA', 'AL', 'BA', 'HR', 'ME', 'MK', 'RS', 'SI', 'KR', 'BD', 'PK', 'LK', 'GH', 'KE', 'NG', 'TZ', 'UG', 'AG', 'AM', 'BS', 'BB', 'BZ', 'BT', 'BW', 'BF', 'CV', 'CW', 'DM', 'FJ', 'GM', 'GE', 'GD', 'GW', 'GY', 'HT', 'JM', 'KI', 'LS', 'LR', 'MW', 'MV', 'ML', 'MH', 'FM', 'NA', 'NR', 'NE', 'PW', 'PG', 'PR', 'WS', 'SM', 'ST', 'SN', 'SC', 'SL', 'SB', 'KN', 'LC', 'VC', 'SR', 'TL', 'TO', 'TT', 'TV', 'VU', 'AZ', 'BN', 'BI', 'KH', 'CM', 'TD', 'KM', 'GQ', 'SZ', 'GA', 'GN', 'KG', 'LA', 'MO', 'MR', 'MN', 'NP', 'RW', 'TG', 'UZ', 'ZW', 'BJ', 'MG', 'MU', 'MZ', 'AO', 'CI', 'DJ', 'ZM', 'CD', 'CG', 'IQ', 'LY', 'TJ', 'VE', 'ET', 'XK']",0.852,0.773,8.0,-2.921,0.0,0.0776,0.187,3.05e-05,0.159,0.907,102.034,4.0

```
- **Twitter**. Tweets with a large amount of meta information. Format: JSON. Average object size - 2581 bytes. Example:
```
{ "_id" : { "$oid" : "59435062a7986c085b072088" }, "text" : "@shaqdarcy hahaha.. soya talaga bro.. :)", "in_reply_to_user_id_str" : "238156878", "id_str" : "133582462910611456", "contributors" : null, "in_reply_to_user_id" : 238156878, "created_at" : "Mon Nov 07 16:31:55 +0000 2011", "in_reply_to_status_id" : { "$numberLong" : "133582357465792513" }, "entities" : { "hashtags" : [  ], "user_mentions" : [ { "screen_name" : "shaqdarcy", "indices" : [ 0, 10 ], "id_str" : "238156878", "name" : "Darcy Nicolas", "id" : 238156878 } ], "urls" : [  ] }, "geo" : null, "source" : "web", "place" : null, "favorited" : false, "truncated" : false, "coordinates" : null, "retweet_count" : 0, "in_reply_to_screen_name" : "shaqdarcy", "user" : { "profile_use_background_image" : true, "favourites_count" : 13, "screen_name" : "JaybeatBolido", "id_str" : "255006912", "default_profile_image" : false, "geo_enabled" : false, "profile_text_color" : "333333", "statuses_count" : 467, "profile_background_image_url" : "http://a0.twimg.com/images/themes/theme1/bg.png", "created_at" : "Sun Feb 20 13:31:09 +0000 2011", "friends_count" : 92, "profile_link_color" : "0084B4", "description" : "I want to be a JEDI.", "follow_request_sent" : null, "lang" : "en", "profile_image_url_https" : "https://si0.twimg.com/profile_images/1614465172/jp_normal.jpg", "profile_background_color" : "C0DEED", "url" : null, "contributors_enabled" : false, "profile_background_tile" : false, "following" : null, "profile_sidebar_fill_color" : "DDEEF6", "protected" : false, "show_all_inline_media" : false, "listed_count" : 1, "location" : "Phillipines-Manila", "name" : "Japhette Pulido", "is_translator" : false, "default_profile" : true, "notifications" : null, "profile_sidebar_border_color" : "C0DEED", "id" : 255006912, "verified" : false, "profile_background_image_url_https" : "https://si0.twimg.com/images/themes/theme1/bg.png", "time_zone" : null, "utc_offset" : null, "followers_count" : 42, "profile_image_url" : "http://a1.twimg.com/profile_images/1614465172/jp_normal.jpg" }, "retweeted" : false, "id" : { "$numberLong" : "133582462910611456" }, "in_reply_to_status_id_str" : "133582357465792513" }

``` 
- **Twitter sentiments** Twitter sentiments data set (we extract only tweet texts). Format: JSON. Average object (tweet) size - 76 bytes. Example:
```
"@alielayus I want to go to promote GEAR AND GROOVE but unfornately no ride there  I may b going to the one in Anaheim in May though"
```
## Prerequisites  

- Java 11+
- Maven 3.x

## Installation and usage

- Clone the repository

- Download datasets from [Release page](https://github.com/carrotdata/membench/releases/tag/0.1)
- Extract datasets (make sure you have installed **Zstandard** compression tools) and copy them into corresponding subfolders of ```data``` directory
- Run ```mvn package``` to build the tool
- Run ```bin/membench.sh``` without command-line arguments to see the **Usage** output.

## Examples

- Run `twitter` benchmark, load 10M records, use 4 threads, server address XXX, port - 1234, mode - load
  ```shell
  bin/membench.sh -b twitter -n 10000000 -t 4 -s XXX -p 1234 -m load
  ```
- Run `airbnb` benchmark, load 20M records, use 16 threads, mode load and read
  ```shell
  bin/membench.sh -b airbnb -n 20000000 -t 16 -m load_read
  ```
Membench supports client side compression (use ```-c gzip```). Do not enable it for ```Memcarrot``` server, because the server does it internally and much more efficiently. 
You can enable client-side compression for vanilla ```memcached``` server. Tests have been performed with compression enabled for ```memcached``` (Gzip codec with default compression level was used) and disabled. Default write/read batch size - 50 (```-a 50```) was used in all tests.

## Memcarrot 0.15 vs Memcached 1.6.29 vs Redis 7.2.5
### Configuration
- Mac OS Sonoma 14.5
- Mac Studio M1, 64GB RAM, 1 TB disk
- Client side compression was ON(zlib) and OFF for ```memcached```
- Number of test threads: 4
- Number of records varied from 10M to 100M across all data sets
- ```memcached``` command line: ```memcached -m 50000 -v```
- `Redis` command line: `redis-server`
- ```Memcarrot``` configuration: compression=ZSTD, level=3, compression page size=8192, storage max size=34359738368, index format=```com.carrotdata.cache.index.SubCompactBaseNoSizeIndexFormat```  
> `memcached`: for ```twitter_sentiments``` and ```ohio``` datasets client compression has been disabled because compression ratio was below 1.0.  
> `Redis`: for ```twitter_sentiments``` dataset client compression has been disabled (`ohio` was with compression enabled)
 
### Results

Table 1. RAM Usage and load throughput. Each result cell contains three numbers: number of objects loaded, server memory usage at the end of a benchmark run and average load throughput in records per second

| Server | airbnb | amazon_product_reviews | arxiv | dblp | github | ohio | reddit | spotify | twitter | twitter_sentiments |
| :---: | :---: | :---: | :---: | :---: | :--: | :---: | :---: | :---: | :---: | :---: |
| Memcarrot 0.15 | 20M, 8.38GB, 356K | 40M, 8.9GB, 535K | 20M, 10.8GB, 302K | 50M, 6.2GB, 680K | 40M, 3.0GB, 734K | 100M, 4.16GB, 805K | 10M, 3.33GB, 368K | 40M, 4GB, 655K | 10M, 5.4GB, 293K | 100M, 5.11GB, 755K |
| memcached 1.6.29 (zlib)| 20M, 19.4GB, - | 40M, 18.23GB, - | 20M, 20.44GB, - | 50M, 18.7GB, - | 40M, 14.2GB, - | 100M, 18.9GB, - | 10M, 13.0GB, - | 40M, 22.0GB, - | 10M, 11.7GB, - | 100M, 16.4GB, - |
| Redis 7.2.5 (zlib)| 20M, 20.4GB, - | 40M, 19.3GB, - | 20M, 21.5GB, - | 50M, 20.0GB, - | 40M, 15.9GB, - | 100M, 23.8GB, - | 10M, 15.2GB, - | 40M, 23.4GB, - | 10M, 12.7GB, - | 100M, 20.3GB, - |
| memcached 1.6.29 (no comp)| 20M, 30.3GB, 518K | 40M, 25.5GB, 576K | 20M, 35.9GB, 521K | 50M, 25.2GB, 670K | 40M, 36.8GB, 582K | 100M, 18.9GB, 644K | 10M, 32.3.0GB, 419K | 40M, 39.8GB, 556K | 10M, 28.9GB, 426K | 100M, 16.4GB, 726K |


Picture 1. Memory usage in GB.
![Memory usage in GB](/assets/memory.png)

Picture 2. Load throughput in Kops
![Load throughput in Kops](/assets/perf.png)

Contact: Vladimir Rodionov vlad@trycarrots.io. 
Copyright (c) Carrot Data, Inc., 2024
