script "revDeployLibrary"

on extensionInitialize
   if the target is me then
      insert the script of me into back
   end if
end extensionInitialize

on extensionFinalize
   if the target is me then
      remove the script of me from back
   end if
end extensionFinalize

////////////////////////////////////////////////////////////////////////////////

-- A stack can be configured to be deployable to any number of different platforms.
-- This presents a usability problem when switching between stacks configured for
-- different platforms.
-- To resolve this, we keep track of the last selected target for each platform and the
-- order in which other platforms were last selected.
-- The 'Test Target' menu disables all targets for which the current stack is not
-- configured for.
-- The current 'Test Target' is determined to be the first platform in the platform choice
-- history for which the stack is configured. This is then used to pick the last chosen
-- target for this platform.

constant kSupportedPlatforms = "iOS Android Desktop Emscripten"

-- The mapping from deploy target to platform
local sDeployTargetPlatforms

-- The currently chosen deploy target for each platform
local sDeployTargets
-- The ordered list of platforms, in order of last choice
local sDeployPlatforms

-- Used to stop DeployAction being executed inside itself
local sDeployActionInProgress

-- This method is invoked by the menu bar when the user elects to test the
-- current stack.
command revIDEDeployAction
   if sDeployActionInProgress then
      exit revIDEDeployAction
   end if
   
   -- Get the stack we are dealing with
   local tStack
   put the mainStack of the topStack into tStack
   
   -- Make sure we save the stack if it needs it
   set the defaultStack to tStack
   if not revSaveCheck(tStack) then
      exit revIDEDeployAction
   end if
   
   -- Get the target
   local tTarget
   put revIDEDeployGetTarget() into tTarget
   
   -- Get the target platform
   local tPlatform
   put sDeployTargetPlatforms[tTarget] into tPlatform
   
   put true into sDeployActionInProgress
   
   -- Invoke the action in the appropriate platform-specific library
   local tError
   if there is a stack ("revDeployLibrary" & tPlatform)  then
      dispatch "deployDo" to stack ("revDeployLibrary" & tPlatform)  with tStack, tTarget
      put the result into tError
   else
      put "Unknown deployment platform - " & tPlatform into tError
   end if
   
   -- Report an error if one occurred
   if tError is not empty then
      if revTestEnvironment() then
         return tError
      else
         answer error tError
      end if
   end if
   
   put false into sDeployActionInProgress
end revIDEDeployAction

-- This method is invoked (currently) by the standalone builder to deploy a mobile
-- platform (not currently used as more work is needed to extricate standalone
-- building from the standalone settings main stack).
command revIDEDeployBuild pPlatform, pTargetStack, pOutputFolder
   -- Make sure we are dealing with main stack
   put the mainStack of stack pTargetStack into pTargetStack
   
   -- Dispatch the action to the platform-specific library
   dispatch "deployBuild" to stack ("revDeployLibrary" & pPlatform) with pTargetStack, pOutputFolder
end revIDEDeployBuild

-- This method is invoked by the menu bar to construct the current list of targets.
-- It returns an array, one element per platform; each element consisting of the list
-- of possible targets.
function revIDEDeployListTargets
   local tTargets
   repeat for each word tPlatform in kSupportedPlatforms
      if there is not a stack ("revDeployLibrary" & tPlatform) then
         next repeat
      end if
      
      dispatch function "deployListTargets" to stack ("revDeployLibrary" & tPlatform) 
      if it is "handled" and the result is not empty then
         put the result into tTargets[tPlatform]
      end if
   end repeat
   return tTargets
end revIDEDeployListTargets

-- This method is invoked by the menu bar when the user selects a different deploy
-- target.
command revIDEDeploySetTarget pTarget
   local tPlatform
   put sDeployTargetPlatforms[pTarget] into tPlatform
   delete item itemOffset(tPlatform, sDeployPlatforms) of sDeployPlatforms
   put tPlatform, sDeployPlatforms into sDeployPlatforms
   put pTarget into sDeployTargets[tPlatform]
   set the cDeployPlatforms of stack "revPreferences" to sDeployPlatforms
   set the cDeployTargets of stack "revPreferences" to sDeployTargets
end revIDEDeploySetTarget

-- This method returns the current target of the 'Deploy' action.
function revIDEDeployGetTarget
   if the mode of the topStack is 1 then
      local tStack
      put the mainStack of the topStack into tStack
      repeat for each item tPlatform in sDeployPlatforms
         if the cRevStandaloneSettings[tPlatform] of stack tStack then
            return sDeployTargets[tPlatform]
         end if
         
         if there is a stack ("revDeployLibrary" & tPlatform) then
            dispatch function "deployListTargets" to stack ("revDeployLibrary" & tPlatform) 
            if it is "handled" and the result is not empty then
               
               local tTarget
               repeat for each line tTarget in the result
                  if the cRevStandaloneSettings[tTarget] of stack tStack then
                     return sDeployTargets[tPlatform]
                  end if
               end repeat
            end if
         end if
      end repeat
   end if
   
   return empty
end revIDEDeployGetTarget

-- This method returns true if deployment is possible with the given platform to the
-- current top stack
function revIDEDeployIsPossible pPlatform
   if the mode of the topStack is not 1 then
      return false
   end if
   
   local tStack
   put the mainStack of the topStack into tStack
   if the cRevStandaloneSettings[pPlatform] of stack tStack then
      return true
   end if
   
   if there is a stack ("revDeployLibrary" & pPlatform) then
      dispatch function "deployListTargets" to stack ("revDeployLibrary" & pPlatform) 
      if it is "handled" and the result is not empty then
         
         local tTarget
         repeat for each line tTarget in the result
            if the cRevStandaloneSettings[tTarget] of stack tStack then
               return true
            end if
         end repeat
      end if
   end if
   
   return false
end revIDEDeployIsPossible

-- This method is called on startup to initialize the deployment library. This includes
-- attempting to auto-configure any SDKs and such if necessary.
command revIDEDeployInitialize
   repeat for each word tPlatform in kSupportedPlatforms
      if there is not a stack ("revDeployLibrary" & tPlatform) then
         next repeat
      end if
      
      dispatch command "deployInitialize" to stack ("revDeployLibrary" & tPlatform) 
   end repeat
   
   local tDeployPlatforms, tDeployTargets
   put the cDeployPlatforms of stack "revPreferences" into tDeployPlatforms
   put the cDeployTargets of stack "revPreferences" into tDeployTargets
   if tDeployPlatforms is not empty then
      put tDeployPlatforms into sDeployPlatforms
      put tDeployTargets into sDeployTargets
   else if the cSimulatorFamily of stack "revPreferences" is not empty then
      put "ios," into sDeployPlatforms
      put the cSimulatorFamily of stack "revPreferences" && "Simulator" && the cSimulatorVersion of stack "revPreferences" into sDeployTargets["ios"] 
   end if
end revIDEDeployInitialize

on revIDEDeployTargetsChanged
   put empty into sDeployTargetPlatforms
   repeat for each word tPlatform in kSupportedPlatforms
      if there is not a stack ("revDeployLibrary" & tPlatform) then
         next repeat
      end if
      
      dispatch function "deployListTargets" to stack ("revDeployLibrary" & tPlatform) 
      if it is "handled" and the result is not empty then
         repeat for each line tTarget in the result
            put tPlatform into sDeployTargetPlatforms[tTarget]
         end repeat
      end if
   end repeat
end revIDEDeployTargetsChanged

////////////////////////////////////////////////////////////////////////////////
//
//  Preferences require access to various specific aspects of deployment config, so these
//  calls provide this in a sane manner.
//

function revIDEDeployIOSIsValidSDK pVersion, pPath
   dispatch function "deployIsValidSDK" to stack "revDeployLibraryiOS" with pVersion, pPath
   return the result
end revIDEDeployIOSIsValidSDK

function revIDEDeployIOSGetSimulatorVersions
   dispatch function "deployGetSimulatorVersions" to stack "revDeployLibraryiOS"
   return the result
end revIDEDeployIOSGetSimulatorVersions

function revIDEDeployIOSGetSDKVersions
   dispatch function "deployGetSDKVersions" to stack "revDeployLibraryiOS"
   return the result
end revIDEDeployIOSGetSDKVersions

// SN-2015-05-01: [[ Refactor iOS SDK Version ]] Wrappers added
function revIDEDeployIOSGetMinimumVersions
   dispatch function "deployGetIosMinimumVersions" to stack "revDeployLibraryiOS"
   return the result
end revIDEDeployIOSGetMinimumVersions

function revIDEDeployIOSGetIphoneOSes
   dispatch function "deployGetIphoneOSes" to stack "revDeployLibraryiOS"
   return the result
end revIDEDeployIOSGetIphoneOSes

command revIDEDeployIOSUpdateSimulators
   dispatch "deployUpdateSimulators" to stack "revDeployLibraryiOS"
end revIDEDeployIOSUpdateSimulators

command revIDEDeployIOSUpdateSDKs
   dispatch "deployUpdateSDKs" to stack "revDeployLibraryiOS"
end revIDEDeployIOSUpdateSDKs

function revIDEDeployIOSUsableSDKs
   dispatch function "deployUsableIosSdk" to stack "revDeployLibraryiOS"
   return the result
end revIDEDeployIOSUsableSDKs

function revIDEDeployIOSGetDeviceSDK pVersion
   dispatch function "deployGetDeviceSDK" to stack "revDeployLibraryiOS" with pVersion
   return the result
end revIDEDeployIOSGetDeviceSDK

function revIDEDeployIOSSanitizeSystemVersion
   dispatch function "deploySanitizeSystemVersion" to stack "revDeployLibraryiOS"
   return the result
end revIDEDeployIOSSanitizeSystemVersion

//SN-2015-04-30: [[ Bug 15300 ]]  Refactor functions used from revDeployLibraryIOS
function revIDEDeployIOSGetInvalidityMessage piOSXcodePairs
   dispatch function "deployGetInvalidityMessage" to stack "revDeployLibraryIOS" with piOSXcodePairs
   return the result
end revIDEDeployIOSGetInvalidityMessage

function revIDEDeployIOSGetLatestSDKInstalled @rVersion
   local tTooltip // Not used
   local tVersions
   dispatch function "deployCheckLatestSDKIsInstalled" to stack "revDeployLibraryIOS" with tTooltip, tVersions
   put the last item of tVersions into tVersions
   if char 1 of tVersions is space then delete char 1 of tVersions
   put tVersions into rVersion
   return the result
end revIDEDeployIOSGetLatestSDKInstalled

////////////////////////////////////////////////////////////////////////////////
//
//  Preferences require access to various specific aspects of deployment config, so these
//  calls provide this in a sane manner.
//

function revIDEDeployAndroidRequiredSDK
   dispatch function "deployRequiredSDK" to stack "revDeployLibraryAndroid"
   return the result
end revIDEDeployAndroidRequiredSDK

function revIDEDeployAndroidIsValidSDK pPath
   dispatch function "deployIsValidSDK" to stack "revDeployLibraryAndroid" with pPath
   return the result
end revIDEDeployAndroidIsValidSDK

function revIDEDeployAndroidIsValidJDK pPath
   dispatch function "deployIsValidJDK" to stack "revDeployLibraryAndroid" with pPath
   return the result
end revIDEDeployAndroidIsValidJDK

function revIDEDeployAndroidGetJDK
   dispatch function "deployGetJDK" to stack "revDeployLibraryAndroid"
   return the result
end revIDEDeployAndroidGetJDK

command revIDEDeployAndroidInitialize
   dispatch "deployInitialize" to stack "revDeployLibraryAndroid"
end revIDEDeployAndroidInitialize


// SN-2015-05-13: [[ AndroidVersions ]] Make the available Android minimum versions
//  something changeable from a script-only stack
function revIDEDeployAndroidGetVersionsList
   local tMap
   dispatch function "deployGetVersionsMap" to stack "revDeployLibraryAndroid"
   put the result into tMap
   combine tMap with return
   return tMap
end revIDEDeployAndroidGetVersionsList

function revIDEDeployAndroidApiFromVersion pVersion
   dispatch function "deployGetApiFromVersion" to stack "revDeployLibraryAndroid" with pVersion
   return the result
end revIDEDeployAndroidApiFromVersion

function revIDEDeployAndroidGetAPIsList
   local tMap,tAPIs
   dispatch function "deployGetVersionsMap" to stack "revDeployLibraryAndroid"
   put the result into tMap
   put the keys of tMap into tAPIs
   replace return with comma in tAPIs
   sort items of tAPIs numeric ascending
   return tAPIs
end revIDEDeployAndroidGetAPIsList

////////////////////////////////////////////////////////////////////////////////
