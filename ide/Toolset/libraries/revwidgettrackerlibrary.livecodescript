script "revwidgettrackerlibrary"

on extensionInitialize
   // SN-2015-05-13: [[ Bug 15365 ]] Make sure that we don't catch
   //  extensionInitialize message if we are not the target
   if the target is not me then
      pass "extensionInitialize"
   end if
   insert the script of me into front
end extensionInitialize

on extensionFinalize
   remove the script of me from front
end extensionFinalize

local lTrackingIcon, lTracking, lCancelID
global gREVCurrentWidget

on mouseUp
  cancel lCancelID
  put false into lTracking
  set the icon of gREVCurrentWidget to 201317
  remove script of me from front
end mouseUp

on mouseDoubleUp
  mouseUp
end mouseDoubleUp

on mouseDoubleDown
  mouseDown
end mouseDoubleDown

on mouseMove x,y
  if lTracking then
    put x,y into tLoc
    if within (gREVCurrentWidget,tLoc) then
      if the icon of gREVCurrentWidget is not lTrackingIcon then
        set the icon of gREVCurrentWidget to lTrackingIcon
        if lTrackingIcon is 201319 then revAddToField
        else if lTrackingIcon is 201318 then revSubtractFromField
      end if
    else
      set the icon of gREVCurrentWidget to 201317
    end if
  end if
end mouseMove

on revStartMouse 
  if the mouseV < (the top of gREVCurrentWidget + 10) then put 201319 into lTrackingIcon
  else put 201318 into lTrackingIcon
  set the icon of gREVCurrentWidget to lTrackingIcon
  if lTrackingIcon is 201319 then revAddToField
  else if lTrackingIcon is 201318 then revSubtractFromField
  put true into lTracking
  if "revTimeMove" is not in the pendingMessages then send "revTimeMove" to gREVCurrentWidget in 250 milliseconds
  put the result into lCancelID
end revStartMouse

on revTimeMove
  if not lTracking then exit revTimeMove
  if the icon of gREVCurrentWidget is 201319 then revAddToField
  else if the icon of gREVCurrentWidget is 201318 then revSubtractFromField
  if "revTimeMove" is not in the pendingMessages then send "revTimeMove" to gREVCurrentWidget in 50 milliseconds
  put the result into lCancelID
end revTimeMove

on mouseRelease
  mouseUp
end mouseRelease

on revAddToField
  lock screen
  if the text of the fld (the cTargetField of gREVCurrentWidget) < the cMaxValue of fld (the cTargetField of gREVCurrentWidget) then add 1 to fld (the cTargetField of gREVCurrentWidget)
  send "closeField" to fld (the cTargetField of gREVCurrentWidget)
  select the text of fld (the cTargetField of gREVCurrentWidget)
  unlock screen
end revAddTofield

on revSubtractFromField
  lock screen
  if the text of the fld (the cTargetField of gREVCurrentWidget) > the cMinValue of fld (the cTargetField of gREVCurrentWidget) then subtract 1 from fld (the cTargetField of gREVCurrentWidget)
  send "closeField" to fld (the cTargetField of gREVCurrentWidget)
  select the text of fld (the cTargetField of gREVCurrentWidget)
  unlock screen
end revSubtractFromField
