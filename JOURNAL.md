[JOURNAL.md.md](https://github.com/user-attachments/files/34524393/JOURNAL.md.md)
---
title: FocusESP
github: https://github.com/sakshamforclaude-source/Foucs-Esp
description: A hardware and software nightmare that is probably a mistake
created_at: 2026-10-15
total_time:
---


(Larping)

Simply, the idea is:

 me watch reel → app sees me watch reel → app sends signal to ESP and closes reel → ESP shocks me (yay)

(Update: I figured out arrows.)
## October 15, 2026: First Prototype

Okay, so I decided to go the software side first because it's easier tbh, and I'm very lazy (and writing an entire Android application manually wasn't particularly appealing).

The original idea was to build something that could track screen time, including apps that don't appear in the normal app drawer **but appear in the hidden app drawer of CMF.** It should've had:

- Blocking Instagram, YouTube (Reels and Shorts especially)
- Pomodoro timer
- Stopwatch and breaks
- Custom schedules
- Daily homework and goal tracking
- An ESP32 connection that triggers a shock when you break a focus rule
- A minimal interface with buttons (THAT WORKS)

My setup involved Ollama for running local AI models and OpenCode as the coding agent.

Getting everything installed was, let's sayyy, adventurous.

First, PowerShell decided that it had a problem with `npm` and blocked the command. I eventually worked around this by using `npm.cmd` instead of `npm`.

Then OpenCode wasn't being recognised inside Android Studio, so I had to launch it using its full file path. Then FINALLY, I INSTALLED THE OLLAMA MODEL inside Android Studio to vibe code.

Then I used it. Oh my god, it was so, so, so good. I am not even kidding. It was the coolest thing ever. I was genuinely surprised that I could just build stuff like that. I made an initial prototype. It had six buttons that opened six interfaces. It was just a black screen and white text. Yeah, it was really great, though. I wanted the minimum interface for a while, but the only problem was that it kind of DIDN'T WORK.

A few major problems became immediately apparent:

- Screen-time tracking just wasn't working (couldn't detect anything; I've no idea why).
- It couldn't reliably block YouTube.
- It couldn't block Instagram Reels.
- It couldn't block YouTube Shorts.
- It couldn't even reliably recognise when YouTube or Instagram was open.

I realised there were actually two separate problems: identifying the installed apps so that they could appear in my app selection list, and retrieving their usage statistics. Identifying that these were two problems was only the first step; I hadn't solved either one yet.

After that, I played with Usage Access and got screen time working for normal (non-hidden) apps. My testing approach was to grant this permission, open hidden applications such as Instagram for a couple of minutes, and check whether their package names and usage durations appeared in Android's usage records. For the hidden apps, of course, that didn't work, and I still couldn't get it to record their screen time. But the test did help me identify the correct Android API and separate the app-discovery problem from the usage-tracking problem, and that got me screen time for apps that weren't hidden.

**Total time spent: 4 hours-ish**

## October 16, 2026:

Then came the biggest challenge: YouTube Shorts and Instagram Reels blocking. My app was supposed to be able to block Shorts and Reels BUT THEY KEPT PLAYING idk whyy.

This meant that detecting the package name alone wasn't sufficient. I needed to identify the actual screen inside the application. To solve this, I started experimenting with Android Accessibility Services and accessibility-tree detection. The idea was to analyse the UI elements displayed on the screen and use them to identify whether someone was watching Shorts or Reels.

I also developed a more structured detection system using components like `RuleRepository`, `RuleEvaluator` and `BlockActionExecutor`. I introduced versioned detection rules (VERSION 2) and experimented with stronger detection signals, including an activity/window-class pattern called `yt_strong_activity` for YouTube Shorts. And guess what happenedd after I did: IT STILL DIDN'T WORK OMG.

I then worked on improving the detection logic. I wanted it to use multiple signals instead of relying on just one identifier, distinguish normal YouTube videos from Shorts, avoid blocking ordinary Instagram content, and handle missing or outdated accessibility information.

I also introduced confidence-based decisions (BLOCK, ALLOW and UNKNOWN), diagnostic logs and safeguards against repeated blocking actions.

I also explored browser URL detection separately.

The idea was to detect URLs such as:

- `youtube.com/shorts/...`
- `instagram.com/reel/...`

This would allow the application to distinguish between normal browser pages and restricted content using URL paths.

I then used the diagnostic logs to try to understand whether the problem was with:

1. Accessibility events not reaching my application.
2. The accessibility tree not exposing the information I needed.
3. The detection rules incorrectly identifying the content.
4. The blocking mechanism not actually executing.

Still nothing worked.

**Total time spent: 5h** (I remember starting at 2 pm and then "finishing" (quitting) around 7:15 pm.)

## October 17, 2026:

While working on the content restrictions, I decided to test whether the basic app-blocking mechanism actually worked. IT WORKEDDDDD!!!!!!!!!!!!!!!!!
When I opened YouTube during a focus session, FocusESP sent me back to the home screen. When I stopped the focus session, YouTube became accessible again.
This was important because it established that at least ONE part of my blocking infrastructure wasn't dysfunctional.

The problem was now more specific: blocking an entire application worked, but identifying and blocking individual sections inside applications didn't.
I began organising app blocking into its own section rather than treating it as something that only existed inside the focus timer.

I also wanted to introduce something called **Hard Mode**.
The idea was simple:

- Start a countdown.
- Lock the selected blocked-app configuration.
- Prevent changing the blocked applications during the session.
- Prevent pausing or resetting the timer.
- Automatically unlock everything when the countdown expires.

I wanted the lock to persist even if the application was restarted.

The coding agent subsequently worked on persisted session state, frozen blocking configurations and expiry handling.
This was intended to prevent someone from simply restarting the application to bypass a hard-focus session. And I got it working beautifully.

**Total time spent: 1h**

## October 18, 2026:

I genuinely got bored of the black backgrounds and the buttons with white text. I decided to give the coding model a prompt to make the UI look prettier, and of course, IT BACKFIRED! The application ended up with black text on a dark, dark background. Reading anything was impossible.

I then decided to move towards a blue-black colour scheme. I wanted something glossy, minimalistic, and with a polished appearance. I experimented with midnight navy, electric blue, ice blue, and cyan colour patterns. I just tried my best, BUT OF COURSE, there weren't consistent colours across backgrounds, buttons, headings, and labels. (HAHAHAHA VIBE CODING IS EASY THEY SAY)

I eventually found some designs on Pinterest and Figma, made a Frankenstein of those designs, and I got it all consistent. It was looking really good.

But of course, the application was so poorly organised. The six buttons on a black background were so much better organised than that. Then I started restructuring the application.

The proposed structure was:

- Screen time
- Focus timer
- App blocking
- Content restrictions
- Schedules and goals
- ESP32

I got this idea from the tabs on Chrome, but the tabs were not at the top; they were at the bottom. Of course, I also decided that the content restrictions (Shorts and Reels) shouldn't depend on whether a focus session was running. App blocking was already separate from the focus timer, and now these should operate independently too. After figuring that out, I implemented it in the app. (And idk how it worked on the first attempt, let's gooo!) (Note: still couldn't get the Reels and Shorts restrictions to work, but I could turn on the buttons without having to start a focus session.)

**Total time spent: 3h**

## October 19, 2026:

I used the application for the rest of October 18, and the parts that did work worked flawlessly (Reels and Shorts blocking still didn't, see above). It actually helped me study for a few more minutes, but I saw another practical problem. I didn't want to simply have a button that said "Block Instagram" and another button that said "Disable blocking." I wanted to make a system that made it harder to give up on restrictions impulsively.

I came up with a hard-block mechanism, separate from Hard Mode, and I used it in the focus timer too. Hard Mode locks everything until a countdown ends. This one is for temporarily disabling a restriction: if I request to disable it, the application gives me a fixed delay and a designated time, and I have to come back at exactly that time to disable the restriction. For example, if I request to disable Instagram restrictions at midnight, the application would give me a time such as 3 am. I must return exactly at 3 am to disable the restriction. If I miss the designated time, the restriction remains active.

I wanted this behaviour for all three restrictions: YouTube Shorts, Instagram Reels, and whole-app blocking. All three should work even if focus mode is off. I got the restrictions working if the focus mode is off, but still, the Instagram Reel and YouTube Shorts restriction did not work in the app. They could be turned on when focus mode is off. 

**Total time spent: 2 hours 47 minutes** (recorded with a stopwatch btw hehehe~)

## October 20, 2026:

Today, I started on the feature I made the app for: the ESP32 integration. Earlier, there was just the ESP32 tab, which didn't do anything. I wanted the application to send a signal to the ESP32 whenever a predefined restriction was violated. I also wanted an ESP32 diagnostic board inside the application so that I could monitor the connection and check whether the commands were being received.

Since Reels and Shorts detection still doesn't work, the signal can currently only be triggered by whole-app blocking, not by the main "watch a reel" case. I got the ESP tab working on the first try but i couldn't really test it because i don't have the hardware

I also wanted to add AI integration to the goals tab of the application (this wasn't in my original feature list), but I decided it was too difficult and not worth adding before the essentials work, so I put it off for now.

**Total time spent: 2h
## October 26, 2026:

 I did research on rapid hole, and it didn't result in anything fruitful. I guess it was just a procrastination method for actually starting my exams, but I made this project online on a program of Hack Club called Fabricate. 
 **Total time spent: 1h**

## November 4, 2026:

I'm TIRED OF IMPROVING THE APP SO I DID SOME HARDWARE REASERCH TODAY (note:i had my exams so i took a REALLLLLY LONG BREAK) I found out: what do I need to make a wearable watch wristband thingy to shock me whenever I watch reels? First of all, I have to make the app work. (We will see that later) secondly, I need to buy an ESP32-C3 mini because they are really small and really great for a wristband. After studying a little bit of electronics, I found out what kind of shocking device I could make that could be safe (ig) 
oh gawd im so scared I READ SOME REDDIT POSTS THERE IS NO WAY IM DESIGNIING SOMETHING THAT SHOCKS ME NO WAYYY I DONT WANNA DIE 
I learned things about Git and GitHub, and I sent the Android focus ESP to GitHub. I also sent the journal.md file to GitHub. 
so um change of plan igggg
 spent some time thinking and researching about this, and I have another idea now. We are going to make the same device with the same ESP32 outputs, but the only thing that is going to be changed is this: instead of making the device that shocks me, I'm going to be inspired by a rubber band. See, when you stretch a rubber band and let it go, it hurts. (REVOLUTIONAYY i know) we are going to be making a device like that, so what I'm going to do is I'm going to be using a push solenoid a very small version, by the way, because I don't want a wrist device to be tall. I want it to be sleek and cool, right? That's the plan I have, to be honest. I've been thinking a lot about how I'm going to make that, and a magnet seems to be cool, but I don't know how I'm going to fit a solenoid in such a small space. I tried sketching a lot of things, but YEAH NO LUCK IG 
 I just thought about "things" for some time, and I made a model in FreeCAD. Let me tell you how it works. 
 This is a screenshot of the model in Paint. The blue part is a rigid part that is fixed. You exert a force F upward on the elastic material, and then you release the force. When you exert a force from A to B, some mechanical or elastic energy is stored in the body, and then you release it, and the force goes to the hand. I'm so smart I tried using a pen, you know like the back of the pen I fixed it to the table with the help of a tape, and then I pulled the pen up and then I released it. So much impulse was generated.![[Screenshot 2026-11-07 145032.png|677]]
 and letssss gooooooooooooo
Now the only that is left is to - Determine if 3D-printed plastic material is elastic enough for the prototype to work.
- Write the code for the ESP32.
- Fix focus ESP and make it better to detect if it works for YouTube Shorts and Instagram Reels.
 - model the wrist band completely inside of free cad 
and it will be done YAYAYAYYAYAYAYYAYAYAAYAYAY time to make focus esp better
**Total time spent: 2h** 
## November 7, 2026:
 now, um I kind of procrastinated for the last few days on making focus ESP better because vibe coding isn't exactly appealing. But see, I have a plan. What I'm going to do is:

1. I'm going to go on GitHub and search up short blockers.
2. I'm going to test a few of the short blockers and the blocker that works 
3. I'm going to clone the git inside of my focus ESP and find out how it makes and how it blocks shorts.
4. I'm going to copy the feature and put it into focus ESP
I kinda feel like mark zukerberg rn
 I found a GitHub repo of a short blocker that actually works. I tested it on a YouTube short, and it works. I tested it on my hidden Instagram, and guess what? ITTT WORKSSSSSSS LETSSSSS GOOOOO https://github.com/atick-faisal/Shorts-Blocker
NO this has a problem too. The YouTube Shorts work fine, but Instagram does not. It blocks everything: it blocks stories and messages. Not workable ARGH ill implement the youube feayure anyways
I HATE ai models Basically, what happened is I opened up my Android Studio. I launched QWEN, whatever the model was, and I got the repository. I gave him instructions, and it just said "no" for some reason. I think I have the screenshot here, but I don't know. I uploaded it on my Instagram story too. It just said no, and it was so stupid.

Then I was like, "I'll download a big model." I downloaded GLM 4.7 Flash, a 19 GB model, and when I tried to run it, it didn't run. Now I'm downloading Qwen 2.5 Codex. With 7 billion parameters, I have 6 GB of VRAM. I don't know why I'm downloading a bigger model again. I spent an hour of my life waiting for GLM 4.7 Flash to download, and it was just a waste to download. Now I just wasted 19 GB of storage and an hour of my life.

No worries, the Qwen 2.5 is loading, and then we will start the "work" again
I cloned the repo inside my FocusESP folder and opened OpenCode in Android Studio with Qwen 2.5 Coder 7B. I gave it a prompt to reverse engineer the repo. And it just... didn't do anything?? It kept trying to "load a specialized skill" called explore over and over and over and OVER. I stopped it and gave it a simpler prompt (no skills, no subagents, just read the files). Then it tried to READ THE ENTIRE FOLDER as if it was a file.OFCCCC

I exited OpenCode, went to the actual Local terminal and ran it myself, and FINALLY the files showed up. The repo was cloned properly the whole time, it was just AI BEING AI

Then I tried to make it do the real task and it made up a path that mixed both projects together (./FocusESP/app/src/main/kotlin/dev/atick/...). Those are two different projects!!!! I was so done at this point. I told it the exact paths and it said "Understood" and then did nothing.

I tried so many things after that:

- Switched to Qwen 3.5 4B (the model that I already had). It ALSO just printed the commands instead of running them.
- Searched for the problem and found a GitHub issue that is literally my exact problem (Windows 11 + Ollama + Qwen 2.5 Coder 7B + OpenCode, prints {"name":"glob"...} as text). It was closed without a fix lol.
- Tried `npm install -g @opencode/cli` to get OpenCode V2 and PowerShell blocked npm (scripts are disabled, again). So I downloaded the Windows zip of OpenCode instead and it launched (version 2.2.6!). But it was running from the opencode folder in Documents and not from my project, so that was useless for now.
- Spent a whole hour of my life on this. Honestly I was SO frustrated.

THEN I just tried something dumb. I stayed on OpenCode 1.19.0, typed /models, scrolled down, picked DeepSeek V4.1 Flash, set it to high, and pasted the integration prompt. AND IT WORKED?!?! HAHAHAHAHAA


While I was fighting the AI I also read the actual code of the blocker myself in the terminal (YouTubeShortsDetector.kt, ShortFormContentBlockerService.kt, AndroidManifest.xml). And it's SO simple!!! I was way overcomplicating it last time with all the confidence scores and activity classes and stuff. What it actually does:

- It's an accessibility service that listens for window changes and content changes
- It looks through the accessibility tree (BFS, max 120 nodes) for a view id that has `reel_progress_bar` in it
- If it finds it, it does GLOBAL_ACTION_BACK
- There's a 1.5 second cooldown so it doesn't spam

THAT'S IT. That's the whole thing. I spent like 3 days on it and the answer was one resource ID, lol. Also noticed that the docs say it checks if the element is visible but the code doesn't actually do that. So I told DeepSeek to not blindly copy it.

I gave DeepSeek a good prompt with all this info  It made YouTubeShortsDetector.kt, plugged it into my existing service, and used my existing ActionGate. Gradle build passed and the unit tests passed.

I put the APK on Google Drive, installed it on my CMF phone, and turned on the Shorts restriction with focus mode off. IT WORKS REALLY REALLY WELL.
(
Only problem: when it blocks a short, the video goes into a tiny floating window .  its because I was sending the user to the home screen. So I made a new prompt: for Shorts, use BACK instead of HOME, and then show a full-screen overlay (TYPE_ACCESSIBILITY_OVERLAY, so I don't need any extra permission) with a message that I can customize in the Content Restrictions screen. TESTEDIT AND IT WORKEDD
I dont remember if i told this but when i tested the repo yt shorts were fine but reels werent the app was blocking messages, stories ALL OF IT 

So ofc I wanted ALL the info from the repo's Instagram detector, so I ran a big batch of terminal commands to print every file (InstagramReelsDetector.kt, the interface, the service, the preferences, the config) and searched for all the instagram stuff. And I found WHY it blocks stories and DMs:

- Condition 1: `clips_tab` is selected -> block (this one is fine)
- Condition 2: if there is NO `feed_tab` found -> block. THIS IS THE PROBLEM. Stories, DMs and fullscreen stuff also don't have the feed tab, so it blocks them
- It also only scans 10 nodes, so it can miss stuff, and the dev even says `clips_viewer` is used for ALL video content

So the rule I made for DeepSeek is: positive evidence only. No evidence != Reels. I'd rather it miss a Reel than block my messages.

DeepSeek built InstagramReelsDetector.kt, removed the broad Instagram rules in my app (the activity name and clips_viewer ones, those were way too broad), and reused the overlay. 143 tests passing. But when I looked at the result there were two problems:

1. A truncated (incomplete) accessibility tree could still be marked as Reels and blocked
2. The tests for Stories and DMs didn't include the selected clips_tab, so they weren't actually testing the hard case

I sent a correction prompt and it fixed both: truncated tree is now ALWAYS ambiguous (never blocks), and there are new tests for story + dm with both reel signals (they give AMBIGUOUS, not block). It also lowered the confidence from 0.9 to 0.6 and documented what is known and what is just a guess. Now 145 tests pass, 0 fail, build successful.

Then i uploaded the apk into my phone AND THE MOMENT OF TRUTH it didnt work ofc HAHAHAHA IVE NO IDEA WHY
But then I looked at the diagnostics screen in the app (the thing I made for exactly this, past me was smart) and this is what it said:

- Capture: pkg=com.instagram.android, nodes=36 (truncated)
- Signals: `clips_tab:selected`, `clips_viewer`, `feed_tab:pre...`
- Decision: UNKNOWN, conf=20%
- Reason: "Incomplete accessibility tree; Reels cannot be confirmed safely."
- Pipeline log full of `ENFORCE DETECTION_UNKNOWN`

So the detector DID find both signals on a real Reels screen. It just refused to block because the tree was marked truncated. That is MY safety fix from earlier (the "truncated tree is always ambiguous" thing, issue 1). I made the detector so careful that it couldn't do its job. The irony is spectacular and I'm not even mad, it's kinda funny. At least now I have real evidence of what my phone's Instagram exposes instead of guessing.

**The plan 

- find out why the snapshot says 36 nodes (truncated). Is it a node limit, a depth limit, or something else? NOT guessing this time, DeepSeek has to actually check
- make the Instagram detector read the live accessibility tree directly (it already has a bounded BFS) instead of only trusting the already-truncated snapshot
- new rule: selected `clips_tab` + `clips_viewer` + no Story/DM evidence = block, even if the snapshot was truncated, as long as the live traversal found the signals itself. If the tree is truncated and the evidence is incomplete or conflicting, still UNKNOWN. I'm NOT deleting the safety check, just making it smarter
- `feed_tab` being present is not a reason to allow, and it being missing is not a reason to block (that was the original repo's bug)
- don't touch YouTube Shorts, it works
- regression tests on the real decision path, then build

I sent DeepSeek the bug-fix prompt for this. AND TADAA IT WORKSSSSSSSS LETS GOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO the reels are blocked the stories and messages stayyy intact YAYAYAYAYAYYAYAYAYAYAYAYAYA
**Total time spent: 5h** 
