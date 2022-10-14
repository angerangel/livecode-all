/* Copyright (C) 2015 LiveCode Ltd.
 
 This file is part of LiveCode.
 
 LiveCode is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License v3 as published by the Free
 Software Foundation.
 
 LiveCode is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or
 FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 for more details.
 
 You should have received a copy of the GNU General Public License
 along with LiveCode.  If not see <http://www.gnu.org/licenses/>.  */

#include "prefix.h"

#include "globdefs.h"
#include "filedefs.h"
#include "objdefs.h"
#include "parsedef.h"


#include "util.h"
#include "mcerror.h"
#include "sellst.h"
#include "stack.h"
#include "card.h"
#include "image.h"
#include "widget.h"
#include "param.h"
#include "osspec.h"
#include "cmds.h"
#include "scriptpt.h"
#include "hndlrlst.h"
#include "debug.h"
#include "redraw.h"
#include "font.h"
#include "chunk.h"
#include "graphicscontext.h"
#include "dispatch.h"
#include "tooltip.h"

#include "globals.h"
#include "context.h"

#include "widget-ref.h"
#include "widget-events.h"

////////////////////////////////////////////////////////////////////////////////

// The current event handling model splits events into two types - active and
// passive.
//
// Passive events are those which are generated by the user in order to generate
// active events. The following events fall into this category:
//     - mouseEnter
//     - mouseLeave
//     - mouseMove
//     - focusEnter
//     - focusLeave
//     - dragEnter
//     - dragLeave
//     - dragMove
//     - touchEnter
//     - touchLeave
//     - touchMove
// Typically the passive events are ones which an owning widget might need to
// process to appropriately change its state based on what active events might
// occur.
//
// Active events are those which arise from an explicit 'action' from the user.
// The following events fall into this category:
//     - mouseDown
//     - mouseUp
//     - mouseCancel
//     - mouseScroll
//     - click
//     - keyDown
//     - keyUp
//     - dragStart
//     - dragFinish
//     - dragDrop
//     - touchBegin
//     - touchEnd
//     - touchCancel
//
// Passive events always bubble up from child widgets to owner widgets whereas
// active events are optionally bubbled up from child widgets by the event
// handler returning 'true' to indicate 'continue bubbling'.

////////////////////////////////////////////////////////////////////////////////

MCWidgetEventManager* MCwidgeteventmanager = nil;

////////////////////////////////////////////////////////////////////////////////

struct MCWidgetEventManager::MCWidgetTouchEvent
{
    bool m_active;
    uinteger_t  m_id;
    uinteger_t  m_index;
    coord_t     m_x;
    coord_t     m_y;
};

static inline void MCValueAssignOptional(MCValueRef& x_target, MCValueRef p_source)
{
    if (x_target == p_source)
        return;
    
    if (x_target != nil)
        MCValueRelease(x_target);
    
    if (p_source != nil)
        MCValueRetain(p_source);
    
    x_target = p_source;
}

////////////////////////////////////////////////////////////////////////////////

MCWidgetEventManager::MCWidgetEventManager() :
  m_mouse_x(FLT_MIN), m_mouse_y(FLT_MAX),
  m_click_x(FLT_MIN), m_click_y(FLT_MAX),
  m_click_time(0),
  m_click_count(0),
  m_click_button(0),
  m_mouse_buttons(0),
  m_mouse_focus(nil),
  m_mouse_grab(nil),
  m_keycode(0),
  m_modifiers(0),
  m_keystring(nil),
  m_keyboard_focus(nil),
  m_drag_target(nil),
  m_target(nil),
  m_doubleclick_time(MCdoubletime),
  m_doubleclick_distance(MCdoubledelta),
  m_check_mouse_focus(false),
  m_touches(),
  m_touch_count(0),
  m_touch_sequence(0),
  m_touched_widget(nullptr),
  m_touch_id(0)
{
}

////////////////////////////////////////////////////////////////////////////////

// These methods are the bridge between the 'old' MCControl world and the new
// Widget world.

void MCWidgetEventManager::event_open(MCWidget* p_widget)
{
    MCWidgetOnOpen(p_widget -> getwidget());
    if (MCcurtool != T_BROWSE)
        event_toolchanged(p_widget, MCcurtool);
}

void MCWidgetEventManager::event_close(MCWidget* p_widget)
{
    if (m_mouse_grab != nil)
    {
        if (MCWidgetGetHost(m_mouse_grab) == p_widget)
        {
            mouseCancel(m_mouse_grab, 0);
        }
    }
    
    if (m_mouse_focus != nil)
    {
        
        if (MCWidgetGetHost(m_mouse_focus) == p_widget)
        {
            mouseLeave(m_mouse_focus);
            MCValueAssignOptional(m_mouse_focus, nil);
        }
    }
    
    /* Issue a cancel touches event. */
    event_cancel_touches(p_widget->getwidget());
    
    MCWidgetOnClose(p_widget -> getwidget());
}

void MCWidgetEventManager::event_kfocus(MCWidget* p_widget)
{
    // WIDGET-TODO: Reinstate FocusEnter
    
#if WIDGET_KEYBOARD_FOCUS
    // Keyboard focus has changed
    m_keyboard_focus = p_widget;
    
    p_widget->OnFocusEnter();
#endif
}

void MCWidgetEventManager::event_kunfocus(MCWidget* p_widget)
{
    // WIDGET-TODO: Reinstate FocusLeave
    
#if WIDGET_KEYBOARD_FOCUS
    // Keyboard focus has changed
    // TODO: does the unfocus *always* happen before the next focus?
    m_keyboard_focus = nil;
    
    p_widget->OnFocusLeave();
#endif
}

Boolean MCWidgetEventManager::event_kdown(MCWidget* p_widget, MCStringRef p_text, KeySym p_key)
{
    // Prevent the IDE from breaking
    if (!widgetIsInRunMode(p_widget))
        return p_widget->MCControl::kdown(p_text, p_key);
    
    // If the control that is the target of the event is not the mouse focus's
    // host widget - then this indicates the sync error has occurred.
    if (!check_mouse_focus(p_widget, "event_kdown"))
        return False;
    
    // Mouse scroll events are sent as key events
    switch (p_key)
    {
        case XK_WheelUp:
            return mouseScroll(m_mouse_focus, 0.0, +1.0);
            
        case XK_WheelDown:
            return mouseScroll(m_mouse_focus, 0.0, -1.0);
            
        case XK_WheelLeft:
            return mouseScroll(m_mouse_focus, +1.0, 0.0);
            
        case XK_WheelRight:
            return mouseScroll(m_mouse_focus, -1.0, 0.0);
            
        default:
            break;
    }

    // WIDGET-TODO: Reinstate keyDown
#if WIDGET_KEYBOARD_FOCUS
    return keyDown(p_widget, p_text, p_key);
#else
    return False;
#endif
}

Boolean MCWidgetEventManager::event_kup(MCWidget* p_widget, MCStringRef p_text, KeySym p_key)
{
    // WIDGET-TODO: Reinstate keyUp
#if WIDGET_KEYBOARD_FOCUS
    return keyUp(p_widget, p_text, p_key);
#else
    return False;
#endif
}

Boolean MCWidgetEventManager::event_mdown(MCWidget* p_widget, uint2 p_which)
{
    // Prevent the IDE from breaking
    if (!widgetIsInRunMode(p_widget))
        return p_widget->MCControl::mdown(p_which);
	
	if (!check_mouse_focus(p_widget, "event_mdown"))
		return False;
	
    return mouseDown(m_mouse_focus, p_which);
}

Boolean MCWidgetEventManager::event_mup(MCWidget* p_widget, uint2 p_which, bool p_release)
{
    // Prevent the IDE from breaking
    if (!widgetIsInRunMode(p_widget))
        return p_widget->MCControl::mup(p_which, p_release);
    
    if (m_mouse_grab == nil ||
        MCWidgetGetHost(m_mouse_grab) != p_widget)
        return False;
    
    if (p_release)
        return mouseCancel(m_mouse_grab, p_which);

    return mouseUp(m_mouse_grab, p_which);
}

Boolean MCWidgetEventManager::event_mfocus(MCWidget* p_widget, int2 p_x, int2 p_y)
{
    if (!widgetIsInRunMode(p_widget))
        return False;
    
    // Compute the new focused widget.
    MCWidgetRef t_focused_widget;
    t_focused_widget = hitTest(p_widget -> getwidget(), p_x, p_y);

    if (t_focused_widget == nil)
    {
        // We can still be focused if the mouse is within the resize handles
        if (p_widget -> getstate(CS_SELECTED)
            && p_widget -> sizehandles(p_x, p_y) != 0)
        {
            t_focused_widget = p_widget -> getwidget();
        }
    }
    
    if (t_focused_widget == nil)
    {
        if (m_mouse_focus != nil &&
            MCWidgetGetHost(m_mouse_focus) != p_widget)
            return False;
        
        if (m_mouse_grab != nil &&
            MCWidgetGetHost(m_mouse_grab) != p_widget)
            return False;
    }
    
    // Check to see if the mouse position has changed.
    bool t_pos_changed;
    t_pos_changed = !(p_x == m_mouse_x && p_y == m_mouse_y);

    // Check to see if the focused widget changed.
    bool t_focused_changed;
    t_focused_changed = !(m_mouse_focus == t_focused_widget);
    
    // Update the current mouse position so that it is correct in mouseEnter and
    // mouseLeave events.
    m_mouse_x = p_x;
    m_mouse_y = p_y;
    
    // Now what we do depends on whether the mouse is grabbed.
    if (m_mouse_grab != nil)
    {
        if (t_focused_changed)
        {
            // If a widget is handling a mouse press event then we want to inform
            // the grabbed widget about enter / leave, but it keeps getting all the
            // move events.
            if (t_focused_widget != m_mouse_grab)
                mouseLeave(m_mouse_grab);
            else if (m_mouse_focus != m_mouse_grab)
                mouseEnter(m_mouse_grab);
        }
        
        // We want to keep track of the focused widget we computed above,
        // but don't want to send it enter / leave in this case.
        MCValueAssignOptional(m_mouse_focus, t_focused_widget);
        
        // We only post a mouse-move message if the position changed.
        if (t_pos_changed)
            mouseMove(m_mouse_grab);
        
        return True;
    }
    else if (t_focused_widget != nil)
    {
        // The mouse has moved into a widget within this control.
        if (t_focused_changed)
        {
            if (m_mouse_focus != nil)
                mouseLeave(m_mouse_focus);
        
            MCValueAssignOptional(m_mouse_focus, t_focused_widget);
            
            mouseEnter(m_mouse_focus);
            
            // If we are in browse mode, then trigger the tooltip.
            if (p_widget -> getstack() -> gettool(p_widget) == T_BROWSE)
                MCtooltip -> settip(p_widget -> gettooltip());
        }
        
        if (t_pos_changed)
            mouseMove(m_mouse_focus);
    }
    else if (t_focused_widget == nil)
    {
        // The mouse has moved out of this widget.
        if (t_focused_changed)
        {
            mouseLeave(m_mouse_focus);
        
            MCValueAssignOptional(m_mouse_focus, t_focused_widget);
        }
    }
    
    // If we are the focused widget, then we handled it.
    return t_focused_widget != nil;
}

void MCWidgetEventManager::event_munfocus(MCWidget* p_widget)
{
    // If a widget is currently grabbed, do nothing.
    if (m_mouse_grab != nil)
    {
        if (MCWidgetGetHost(m_mouse_grab) == p_widget)
        {
            mouseCancel(m_mouse_grab, 0);
        }
    }

    // If the unfocused widget is the currently focused widget, then
    // leave it.
    if (m_mouse_focus != nil &&
		MCWidgetGetHost(m_mouse_focus) == p_widget)
    {
        mouseLeave(m_mouse_focus);
        MCValueRelease(m_mouse_focus);
        m_mouse_focus = nil;
    }
}

void MCWidgetEventManager::event_mdrag(MCWidget* p_widget)
{
    // WIDGET-TODO: Reinstate DragStart
#if WIDGET_DRAG_MESSAGES
    // If this widget is not a drag source, reject the drag attempt
    bool t_drag_accepted;
    t_drag_accepted = p_widget->isDragSource();
    if (t_drag_accepted)
        p_widget->OnDragStart(t_drag_accepted);
    
    if (t_drag_accepted)
        MCdragtargetptr = p_widget;
    else
        MCdragtargetptr = nil;
#endif
}

MCObject *MCWidgetEventManager::event_hittest(MCWidget* p_widget, int32_t x, int32_t y)
{
    MCWidgetRef t_target;
    if (!MCWidgetOnHitTest(p_widget -> getwidget(), MCGPointMake(x, y), t_target))
        t_target = p_widget -> getwidget();
 
    // AL-2015-07-29: [[ Bug ]] Ensure nil is returned if there is no hit.
    if (t_target != nil)
        return MCWidgetGetHost(t_target);
    
    return nil;
}

void MCWidgetEventManager::event_toolchanged(MCWidget* p_widget, Tool p_tool)
{
    MCWidgetOnToolChanged(p_widget -> getwidget(), p_tool);
}

void MCWidgetEventManager::event_layerchanged(MCWidget* p_widget)
{
    MCWidgetOnLayerChanged(p_widget -> getwidget());
}

void MCWidgetEventManager::event_visibilitychanged(MCWidget* p_widget, bool p_visible)
{
    /* Issue a cancel touches event if we are going invisible */
    if (!p_visible)
    {
        event_cancel_touches(p_widget->getwidget());
    }
            
    MCWidgetOnVisibilityChanged(p_widget -> getwidget(), p_visible);
}

Boolean MCWidgetEventManager::event_doubledown(MCWidget* p_widget, uint2 p_which)
{
    // Prevent the IDE from breaking
    if (!widgetIsInRunMode(p_widget))
        return p_widget->MCControl::doubledown(p_which);
    
    // Because we do gesture recognition ourselves, treat this as a normal click
    return event_mdown(p_widget, p_which);
}

Boolean MCWidgetEventManager::event_doubleup(MCWidget* p_widget, uint2 p_which)
{
    // Prevent the IDE from breaking
    if (!widgetIsInRunMode(p_widget))
        return p_widget->MCControl::doubleup(p_which);
    
    // Because we do gesture recognition ourselves, treat this as a normal click
    return event_mup(p_widget, p_which, false);
}

void MCWidgetEventManager::event_timer(MCWidget* p_widget, MCNameRef p_message, MCParameter* p_parameters)
{
    if (p_message != MCM_internal ||
        p_parameters == nil)
        return;
    
    MCWidgetRef t_widget;
    t_widget = (MCWidgetRef)p_parameters -> getvalueref_argument();
    MCWidgetOnTimer(t_widget);
}

void MCWidgetEventManager::event_setrect(MCWidget* p_widget, const MCRectangle& p_rectangle)
{
    MCWidgetOnGeometryChanged(p_widget -> getwidget());
}

void MCWidgetEventManager::event_recompute(MCWidget* p_widget)
{
    // This gets called whenever certain parent (group, card, stack) properties
    // are changed. Unfortunately, we have no information as to *what* changed.
    MCWidgetOnParentPropertyChanged(p_widget -> getwidget());
}

void MCWidgetEventManager::event_paint(MCWidget *p_widget, MCGContextRef p_gcontext)
{
    MCWidgetOnPaint(p_widget -> getwidget(), p_gcontext);
}

bool MCWidgetEventManager::event_touch(MCWidget* p_widget, uint32_t p_id, MCEventTouchPhase p_phase, int2 p_x, int2 p_y)
{
    if (!widgetIsInRunMode(p_widget))
    {
        return false;
    }
    
    /* If a touch is just starting, and this is the first touch then we need
     * to compute the touched widget. */
    MCWidgetRef t_touched_widget = m_touched_widget;
    if (p_phase == kMCEventTouchPhaseBegan)
    {
        if (t_touched_widget == nullptr)
        {
            t_touched_widget = hitTest(p_widget->getwidget(), p_x, p_y);
        }
    }
    
    /* If we don't have a touched widget, then ignore the touch. */
    if (t_touched_widget == nullptr)
    {
        return false;
    }
    
    /* If we have a touched widget, but it doesn't want touch, let default
     * processing occur. */
    if (!MCWidgetHandlesTouchEvents(t_touched_widget))
    {
        return false;
    }
    
    /* See if there is already a slot for the given touch. If none is found
     * and this is not a begin, we ignore it - otherwise we create one. */
    uinteger_t t_slot;
    if (!findTouchSlotById(p_id, t_slot))
    {
        if (p_phase == kMCEventTouchPhaseBegan)
        {
            t_slot = allocateTouchSlot();
            m_touches[t_slot].m_id = p_id;
        }
        else
        {
            return false;
        }
    }
    else if (p_phase == kMCEventTouchPhaseBegan)
    {
        /* This shouldn't happen - it would mean we are getting two began
         * phases for the same touch, so we ignore it. */
        return false;
    }
    
    /* Assign the touched widget (which will be the same as the widget which
     * was touched by the first touch in a sequence - i.e. the first touched
     * widget grabs all touches. */
    MCValueAssign(m_touched_widget, t_touched_widget);
    
    /* Update the position information in the touch. */
    m_touches[t_slot].m_x = p_x;
    m_touches[t_slot].m_y = p_y;
    
    /* Update the 'current' touch id - i.e the touch which is pertinent to
     * this event. */
    m_touch_id = p_id;
    
    /* Process the specific kind of touch. */
    switch (p_phase)
    {
        case kMCEventTouchPhaseBegan:
            bubbleEvent(m_touched_widget, MCWidgetOnTouchStart);
            break;
            
        case kMCEventTouchPhaseMoved:
            bubbleEvent(m_touched_widget, MCWidgetOnTouchMove);
            break;
            
        case kMCEventTouchPhaseEnded:
            bubbleEvent(m_touched_widget, MCWidgetOnTouchFinish);
            break;
            
        case kMCEventTouchPhaseCancelled:
            bubbleEvent(m_touched_widget, MCWidgetOnTouchCancel);
            break;
    }
    
    /* The touch id is only valid whilst an event is happening */
    m_touch_id = 0;
    
    /* If the phase ends the touch, then free the slot and release the touched
     * widget if there are no other touches. */
    if (p_phase == kMCEventTouchPhaseEnded ||
        p_phase == kMCEventTouchPhaseCancelled)
    {
        freeTouchSlot(t_slot);
        if (m_touch_count == 0)
        {
            MCValueAssignOptional(m_touched_widget, nullptr);
            m_touch_sequence = 0;
        }
    }
    
    return true;
}

void MCWidgetEventManager::event_cancel_touches(MCWidgetRef p_widget)
{
    /* If there is a touched widget, and the p_widget is an ancestor of it then
     * we must make sure all touches are cancelled. */
    if (m_touched_widget != nullptr &&
        MCWidgetIsAncestorOf(p_widget, m_touched_widget))
    {
        /* Loop through all touches and cancel the active ones. This will also
         * cause m_touched_widget to be unset after the last active touch is
         * cancelled. */
        for(uindex_t i = 0; i < m_touches.Size(); i++)
        {
            if (m_touches[i].m_active)
            {
                event_touch(MCWidgetGetHost(p_widget), m_touches[i].m_id, kMCEventTouchPhaseCancelled, m_touches[i].m_x, m_touches[i].m_y);
            }
        }
    }
}

void MCWidgetEventManager::event_gesture_begin(MCWidget* p_widget)
{
    // Not implemented
}

void MCWidgetEventManager::event_gesture_end(MCWidget* p_widget)
{
    // Not implemented
}

void MCWidgetEventManager::event_gesture_magnify(MCWidget* p_widget, real32_t p_factor)
{
    // Not implemented
}

void MCWidgetEventManager::event_gesture_rotate(MCWidget* p_widget, real32_t p_radians)
{
    // Not implemented
}

void MCWidgetEventManager::event_gesture_swipe(MCWidget* p_widget, real32_t p_delta_x, real32_t p_delta_y)
{
    // Not implemented
}

void MCWidgetEventManager::event_dnd_drop(MCWidget* p_widget)
{
    //p_widget->OnDragDrop();
}

void MCWidgetEventManager::event_dnd_end(MCWidget* p_widget)
{
    //p_widget->OnDragFinish();
}

////////////////////////////////////////////////////////////////////////////////

void MCWidgetEventManager::widget_appearing(MCWidgetRef p_widget)
{
    if (m_mouse_focus != nil &&
        MCWidgetIsAncestorOf(m_mouse_focus, p_widget))
        event_mfocus(MCWidgetGetHost(p_widget), (int2)m_mouse_x, (int2)m_mouse_y);
    
}

void MCWidgetEventManager::widget_disappearing(MCWidgetRef p_widget)
{
    if (m_mouse_focus != nil &&
        MCWidgetIsAncestorOf(p_widget, m_mouse_focus))
        event_mfocus(MCWidgetGetHost(p_widget), (int2)m_mouse_x, (int2)m_mouse_y);
    
    if (m_mouse_grab != nil &&
        MCWidgetIsAncestorOf(p_widget, m_mouse_grab))
        mouseCancel(p_widget, 0);
    
    /* Cancel any touches */
    event_cancel_touches(p_widget);
}

void MCWidgetEventManager::widget_sync(void)
{
    if (!m_check_mouse_focus)
        return;
}

////////////////////////////////////////////////////////////////////////////////

MCWidgetRef MCWidgetEventManager::GetGrabbedWidget(void) const
{
    return m_mouse_grab;
}

MCWidgetRef MCWidgetEventManager::GetTargetWidget(void) const
{
    return m_target;
}

MCWidgetRef MCWidgetEventManager::SetTargetWidget(MCWidgetRef p_widget)
{
    MCWidgetRef t_old_target;
    t_old_target = m_target;
    m_target = p_widget;
    return t_old_target;
}

void MCWidgetEventManager::GetSynchronousMousePosition(coord_t& r_x, coord_t& r_y) const
{
    r_x = m_mouse_x;
    r_y = m_mouse_y;
}

void MCWidgetEventManager::GetSynchronousClickPosition(coord_t& r_x, coord_t& r_y) const
{
    r_x = m_click_x;
    r_y = m_click_y;
}

void MCWidgetEventManager::GetSynchronousClickButton(unsigned int& r_button) const
{
    r_button = m_click_button;
}

void MCWidgetEventManager::GetSynchronousClickCount(unsigned int& r_count) const
{
    r_count = m_click_count;
}

void MCWidgetEventManager::GetAsynchronousMousePosition(coord_t& r_x, coord_t& r_y) const
{
    r_x = MCmousex;
    r_y = MCmousey;
}

void MCWidgetEventManager::GetAsynchronousClickPosition(coord_t& r_x, coord_t& r_y) const
{
    r_x = MCclicklocx;
    r_y = MCclicklocy;
}

uindex_t MCWidgetEventManager::GetTouchCount(void)
{
    return m_touch_count;
}

bool MCWidgetEventManager::GetActiveTouch(integer_t& r_index)
{
    if (m_touch_id == 0)
    {
        return false;
    }
    
    uindex_t t_slot;
    if (!findTouchSlotById(m_touch_id, t_slot))
    {
        return false;
    }
    
    r_index = m_touches[t_slot].m_index;
    
    return true;
}

bool MCWidgetEventManager::GetTouchPosition(integer_t p_index, MCPoint& r_point)
{
    uinteger_t t_slot;
    if (!findTouchSlotByIndex(p_index, t_slot))
    {
        return false;
    }
    
    r_point.x = m_touches[t_slot].m_x;
    r_point.y = m_touches[t_slot].m_y;
    
    return true;
}

static compare_t _MCSortWidgetTouchIndexes(const MCValueRef *p_left, const MCValueRef *p_right)
{
    MCAssert(MCValueGetTypeCode(*p_left) == kMCValueTypeCodeNumber);
    MCAssert(MCValueGetTypeCode(*p_right) == kMCValueTypeCodeNumber);
    
    return MCNumberCompareTo(MCNumberRef(*p_left), MCNumberRef(*p_right));
}

bool MCWidgetEventManager::GetTouchIDs(MCProperListRef &r_touch_ids)
{
    MCAutoProperListRef t_list;
    if (!MCProperListCreateMutable(&t_list))
    {
        return false;
    }
    
    for (uinteger_t i = 0; i < m_touches.Size(); i++)
    {
        if (m_touches[i].m_active)
        {
            MCAutoNumberRef t_index;
            if (!MCNumberCreateWithInteger(m_touches[i].m_index, &t_index))
            {
                return false;
            }
            
            if (!MCProperListPushElementOntoBack(*t_list, &t_index))
            {
                return false;
            }
        }
    }
    
    // indexes may be out of order if filling inactive slots so sort
    if (!MCProperListSort(*t_list, false, _MCSortWidgetTouchIndexes))
        return false;
        
    if (!t_list.MakeImmutable())
    {
        return false;
    }
    
    r_touch_ids = t_list.Take();
    
    return true;
}


////////////////////////////////////////////////////////////////////////////////

MCWidgetRef MCWidgetEventManager::hitTest(MCWidgetRef p_widget, coord_t x, coord_t y)
{
    // If an error is thrown whilst hit-testing, then we ignore the widget.
    MCWidgetRef t_target;
    if (!MCWidgetOnHitTest(p_widget, MCGPointMake(x, y), t_target))
        t_target = nil;
    return t_target;
}

void MCWidgetEventManager::mouseMove(MCWidgetRef p_widget)
{
    // WIDGET-TODO: Reinstate DragMove
#if WIDGET_DRAG_MESSAGES
    // Update the mouse coordinates
    m_mouse_x = p_x;
    m_mouse_y = p_y;
    
    if (MCdispatcher->isdragtarget())
        p_widget->OnDragMove(p_x, p_y);
    else
        p_widget->OnMouseMove(p_x, p_y);
#endif
    
    if (p_widget != nil)
        alwaysBubbleEvent(p_widget, MCWidgetOnMouseMove);
}

void MCWidgetEventManager::mouseEnter(MCWidgetRef p_widget)
{
    // WIDGET-TODO: Reinstate DragEnter
#if WIDGET_DRAG_MESSAGES
    if (MCdispatcher->isdragtarget())
    {
        // Set this widget as the drag target if it would accept the drop
        bool t_accepted;
        MCdragaction = DRAG_ACTION_NONE;
        p_widget->OnDragEnter(t_accepted);
        if (t_accepted)
            MCdragdest = p_widget;
        else
            MCdragdest = nil;
    }
    else
        p_widget->OnMouseEnter();
#endif
    
    if (p_widget != nil)
    {
        alwaysBubbleEvent(p_widget, MCWidgetOnMouseEnter);
        // If the mouse is entering the root widget, make sure
        // the focused member variable of the MCWidget object
        // is set.
        if (MCWidgetIsRoot(p_widget))
            MCWidgetGetHost(p_widget) -> SetFocused(true);
    }
}

void MCWidgetEventManager::mouseLeave(MCWidgetRef p_widget)
{
    // WIDGET-TODO: Reinstate DragLeave
#if WIDGET_DRAG_MESSAGES
    if (MCdispatcher->isdragtarget())
    {
        p_widget->OnDragLeave();
        MCdragdest = nil;
        MCdragaction = DRAG_ACTION_NONE;
    }
    else
        p_widget->OnMouseLeave();
#endif
    
    if (p_widget != nil)
    {
        alwaysBubbleEvent(p_widget, MCWidgetOnMouseLeave);
        // If the mouse is leaving the root widget, make sure
        // the focused member variable of the MCWidget object
        // is nil.
        if (MCWidgetIsRoot(p_widget))
            MCWidgetGetHost(p_widget) -> SetFocused(false);
    }
}

bool MCWidgetEventManager::mouseDown(MCWidgetRef p_widget, uinteger_t p_which)
{
    // If the button is already down, do nothing.
    if ((m_mouse_buttons & (1 << p_which)) != 0)
        return true;
    
    // If the mouse buttons is currently 0 then this is a transition from no
    // click button to a click button.
    if (m_mouse_buttons == 0)
    {
        MCValueAssign(m_mouse_grab, p_widget);
        m_click_button = p_which;
    }
    
    // Mouse button is down
    m_mouse_buttons |= (1 << p_which);
    
    MCWidgetGetHost(p_widget) -> setstate(True, CS_MFOCUSED);
    
    if (!widgetIsInRunMode(MCWidgetGetHost(p_widget)))
        return false;
    
    // If the button is not the click button, do nothing.
    if (p_which != m_click_button)
        return true;
    
    // Do the position change and time since the last click make this a double
    // (or triple or more...) click?
    if ((MCeventtime <= m_click_time + m_doubleclick_time) &&
        (fabsf(m_mouse_x - m_click_x) <= m_doubleclick_distance) &&
        (fabsf(m_mouse_y - m_click_y) <= m_doubleclick_distance))
    {
        // Within the limits - this is a multiple-click event
        m_click_count += 1;
    }
    else
    {
        // Outside the limits. Only a single click.
        m_click_count = 1;
    }
    
    // The click position is updated regardless of what happens.
    m_click_x = m_mouse_x;
    m_click_y = m_mouse_y;
    m_click_time = MCeventtime;
    m_click_button = p_which;
    
    bubbleEvent(p_widget, MCWidgetOnMouseDown);
    
    return true;
}

bool MCWidgetEventManager::mouseUp(MCWidgetRef p_widget, uinteger_t p_which)
{
    // If the given button isn't actually down, do nothing.
    if ((m_mouse_buttons & (1 << p_which)) == 0)
        return true;
    
    // Mouse button is no longer down
    m_mouse_buttons &= ~(1 << p_which);
    
    // We only do anything about mouseUps if transitioning to no buttons being
    // pressed.
    if (m_mouse_buttons == 0)
    {
        MCValueRelease(m_mouse_grab);
        m_mouse_grab = nil;
        
        MCWidgetGetHost(p_widget) -> setstate(False, CS_MFOCUSED);
        
        if (!widgetIsInRunMode(MCWidgetGetHost(p_widget)))
            return false;
    
        // When we get the final mouseUp, the actual button press is tied to
        // the click button.
        mouseClick(p_widget, m_click_button);
        
        // Reset the click button.
        m_click_button = 0;
    }
    
    return true;
}

void MCWidgetEventManager::mouseClick(MCWidgetRef p_widget, uinteger_t p_which)
{
    // Send the mouse up event before the click recognition
    bubbleEvent(p_widget, MCWidgetOnMouseUp);
    
    bubbleEvent(p_widget, MCWidgetOnClick);
}

bool MCWidgetEventManager::mouseCancel(MCWidgetRef p_widget, uinteger_t p_which)
{
    // When we get a mouseCancel we completely cancel the current click which
    // means resetting mouse buttons and click button to 0.
    
    m_click_button = 0;
    
    m_mouse_buttons = 0;
    
    MCValueRelease(m_mouse_grab);
    m_mouse_grab = nil;
    
    if (!widgetIsInRunMode(MCWidgetGetHost(p_widget)))
        return false;
    
    // Send a mouse release event if the widget handles it
    bubbleEvent(p_widget, MCWidgetOnMouseCancel);
    
    return true;
}

struct bubble_mouse_scroll_state
{
    real32_t x, y;
    static bool action(void *context, MCWidgetRef widget, bool& r_bubble)
    {
        bubble_mouse_scroll_state *self;
        self = (bubble_mouse_scroll_state *)context;
        return MCWidgetOnMouseScroll(widget, self -> x, self -> y, r_bubble);
    }
};

bool MCWidgetEventManager::mouseScroll(MCWidgetRef p_widget, real32_t p_delta_x, real32_t p_delta_y)
{
    if (!widgetIsInRunMode(MCWidgetGetHost(p_widget)))
        return false;
    
    bubble_mouse_scroll_state t_state;
    t_state . x = p_delta_x;
    t_state . y = p_delta_y;
    doBubbleEvent(false, p_widget, bubble_mouse_scroll_state::action, &t_state);
                  
    return false;
}

////////////////////////////////////////////////////////////////////////////////

bool MCWidgetEventManager::keyDown(MCWidgetRef p_widget, MCStringRef p_string, KeySym p_key)
{
    // WIDGET-TODO: Reinstate keyDown
#if WIDGET_KEYBOARD_FOCUS
    // Todo: key gesture (shortcuts, accelerators, etc) processing

    // Has there been a change of modifiers?
    if (MCmodifierstate != m_modifiers)
    {
        m_modifiers = MCmodifierstate;
        p_widget->OnModifiersChanged(m_modifiers);
    }
    
    // Ignore if send to the wrong widget
    if (p_widget != m_keyboard_focus)
        return false;
    
    if (!widgetIsInRunMode(p_widget))
        return false;
    
    // Does the widget want key press events?
    bool t_handled = false;
    if (p_widget->handlesKeyPress() && !MCStringIsEmpty(p_string))
    {
        p_widget->OnKeyPress(p_string);
        t_handled = true;
    }
    
    return t_handled;
#else
    return false;
#endif
}

bool MCWidgetEventManager::keyUp(MCWidgetRef p_widget, MCStringRef p_string, KeySym p_key)
{
    // WIDGET-TODO: Reinstate keyUp
#if WIDGET_KEYBOARD_FOCUS
    // Todo: key gesture (shortcuts, accelerators, etc) processing
    
    // Has there been a change of modifiers?
    if (MCmodifierstate != m_modifiers)
    {
        m_modifiers = MCmodifierstate;
        p_widget->OnModifiersChanged(m_modifiers);
    }
    
    // Ignore if send to the wrong widget
    if (p_widget != m_keyboard_focus)
        return false;
    
    if (!widgetIsInRunMode(p_widget))
        return false;
    
    // If the widget handles key press events, treat this as handled (even
    // though we don't send another message to say the key has been released)
    return p_widget->handlesKeyPress();
#else
    return false;
#endif
}


////////////////////////////////////////////////////////////////////////////////

uinteger_t MCWidgetEventManager::allocateTouchSlot()
{
    // Search the list for an empty slot
    uinteger_t i = 0;
    for (; i < m_touches.Size(); i++)
    {
        // Look for a slot which is not active.
        if (!m_touches[i].m_active)
        {
            break;
        }
    }
    
    if (i == m_touches.Size())
    {
        // No empty slots found. Extend the array.
        /* UNCHECKED */ m_touches.Extend(i + 1);
    }
    
    m_touches[i].m_active = true;
    m_touches[i].m_index = ++m_touch_sequence;
    
    m_touch_count += 1;
    
    return i;
}

bool MCWidgetEventManager::findTouchSlotById(uinteger_t p_id, uinteger_t& r_which)
{
    // Search the list for a touch event with the given ID
    for (uinteger_t i = 0; i < m_touches.Size(); i++)
    {
        if (m_touches[i].m_active &&
            m_touches[i].m_id == p_id)
        {
            r_which = i;
            return true;
        }
    }
    
    // Could not find any touch event with that ID
    return false;
}

bool MCWidgetEventManager::findTouchSlotByIndex(uinteger_t p_index, uinteger_t& r_which)
{
    // Search the list for a touch event with the given ID
    for (uinteger_t i = 0; i < m_touches.Size(); i++)
    {
        if (m_touches[i].m_active &&
            m_touches[i].m_index == p_index)
        {
            r_which = i;
            return true;
        }
    }
    
    // Could not find any touch event with that ID
    return false;
}

void MCWidgetEventManager::freeTouchSlot(uinteger_t p_which)
{
    // Mark as free by clearing the widget pointer for the event
    m_touches[p_which].m_active = false;
    m_touch_count -= 1;
}

////////////////////////////////////////////////////////////////////////////////

typedef bool (*MCWidgetVoidBubbleMethodPtr)(MCWidgetRef, bool&);

static bool call_void_bubble_method(void *context, MCWidgetRef p_widget, bool& r_bubble)
{
    MCWidgetVoidBubbleMethodPtr t_action;
    t_action = (MCWidgetVoidBubbleMethodPtr)context;
    return t_action(p_widget, r_bubble);
}

bool MCWidgetEventManager::widgetIsInRunMode(MCWidget *p_widget)
{
    return p_widget->isInRunMode();
}

bool MCWidgetEventManager::bubbleEvent(MCWidgetRef p_target, bool (*p_action)(MCWidgetRef, bool&))
{
    return doBubbleEvent(false, p_target, call_void_bubble_method, (void *)p_action);
}

bool MCWidgetEventManager::alwaysBubbleEvent(MCWidgetRef p_target, bool (*p_action)(MCWidgetRef, bool&))
{
    return doBubbleEvent(true, p_target, call_void_bubble_method, (void *)p_action);
}

bool MCWidgetEventManager::doBubbleEvent(bool p_always_bubble, MCWidgetRef p_target, bool (*p_action)(void *, MCWidgetRef, bool&), void *p_context)
{
    bool t_success;
    t_success = true;
    
    MCWidgetRef t_old_target;
    t_old_target = m_target;
    
    m_target = p_target;
    
    for(;;)
    {
        bool t_bubble;
        if (m_target == p_target)
            m_target = kMCNull;
        
        if (!p_action(p_context, p_target, t_bubble))
        {
            t_success = false;
            t_bubble = true;
        }
        
        if (m_target == kMCNull)
            m_target = p_target;
        
        if (p_always_bubble)
            t_bubble = true;
        
        if (!t_bubble)
            break;
        
        m_target = p_target;
        
        p_target = MCWidgetGetOwner(p_target);
        if (p_target == nil)
            break;
    }
    
    m_target = t_old_target;
    
    return t_success;
}

////////////////////////////////////////////////////////////////////////////////

bool MCWidgetEventManager::check_mouse_focus(MCWidget *p_widget, const char *p_event)
{
	if (m_mouse_focus != nil &&
		MCWidgetGetHost(m_mouse_focus) == p_widget)
		return true;
	
	MCLog("mfocus-sync-error: %s invoked on widget (%p) which is not m_mouse_focus (%p)", p_event, p_widget -> getwidget(), m_mouse_focus);
	
	return false;
}

////////////////////////////////////////////////////////////////////////////////
