"use client"

import * as React from "react"

import { cn } from "@/lib/utils"
import { Input } from "@/components/ui/input"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { hslToHex } from "@/lib/color-utils"

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

function hexToHslComponents(hex: string): { h: number; s: number; l: number } | null {
  const match = /^#([0-9a-fA-F]{6})$/.exec(hex)
  if (!match) return null

  const r = parseInt(match[1].slice(0, 2), 16) / 255
  const g = parseInt(match[1].slice(2, 4), 16) / 255
  const b = parseInt(match[1].slice(4, 6), 16) / 255

  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  const delta = max - min

  let h = 0
  let s = 0
  const l = (max + min) / 2

  if (delta !== 0) {
    s = delta / (1 - Math.abs(2 * l - 1))
    if (max === r) {
      h = ((g - b) / delta + (g < b ? 6 : 0)) / 6
    } else if (max === g) {
      h = ((b - r) / delta + 2) / 6
    } else {
      h = ((r - g) / delta + 4) / 6
    }
  }

  return { h: h * 360, s, l }
}

// Convert HSL to SB (saturation/brightness) coordinates used in the 2D field.
// The 2D field works in HSV (Hue/Saturation/Value) space which is more
// intuitive for a color picker. We convert HSL → HSV internally.
function hslToHsv(h: number, s: number, l: number): { h: number; s: number; v: number } {
  const v = l + s * Math.min(l, 1 - l)
  const sv = v === 0 ? 0 : 2 * (1 - l / v)
  return { h, s: sv, v }
}

function hsvToHsl(h: number, s: number, v: number): { h: number; s: number; l: number } {
  const l = v * (1 - s / 2)
  const sl = l === 0 || l === 1 ? 0 : (v - l) / Math.min(l, 1 - l)
  return { h, s: sl, l }
}

// ---------------------------------------------------------------------------
// Drag helper hook
// ---------------------------------------------------------------------------

type DragHandler = (x: number, y: number, rect: DOMRect) => void

function useDrag(onDrag: DragHandler) {
  const rectRef = React.useRef<DOMRect | null>(null)
  const onDragRef = React.useRef(onDrag)
  onDragRef.current = onDrag

  // Stable handler refs so add/removeEventListener use the same function identity
  const handlersRef = React.useRef<{
    onMouseMove: (e: MouseEvent) => void
    onTouchMove: (e: TouchEvent) => void
    stop: () => void
  } | null>(null)

  if (!handlersRef.current) {
    const move = (clientX: number, clientY: number) => {
      if (!rectRef.current) return
      onDragRef.current(clientX, clientY, rectRef.current)
    }

    const stop = () => {
      rectRef.current = null
      window.removeEventListener("mousemove", handlersRef.current!.onMouseMove)
      window.removeEventListener("mouseup", handlersRef.current!.stop)
      window.removeEventListener("touchmove", handlersRef.current!.onTouchMove)
      window.removeEventListener("touchend", handlersRef.current!.stop)
    }

    handlersRef.current = {
      onMouseMove: (e: MouseEvent) => move(e.clientX, e.clientY),
      onTouchMove: (e: TouchEvent) => {
        if (e.touches[0]) move(e.touches[0].clientX, e.touches[0].clientY)
      },
      stop,
    }
  }

  const startDrag = React.useCallback((e: React.MouseEvent | React.TouchEvent) => {
    const target = e.currentTarget as HTMLElement
    rectRef.current = target.getBoundingClientRect()
    const { onMouseMove, onTouchMove, stop } = handlersRef.current!

    if ("touches" in e) {
      if (e.touches[0]) onDragRef.current(e.touches[0].clientX, e.touches[0].clientY, rectRef.current)
      window.addEventListener("touchmove", onTouchMove, { passive: true })
      window.addEventListener("touchend", stop)
    } else {
      onDragRef.current(e.clientX, e.clientY, rectRef.current)
      window.addEventListener("mousemove", onMouseMove)
      window.addEventListener("mouseup", stop)
    }
  }, [])

  return { startDrag }
}

// ---------------------------------------------------------------------------
// ColorPicker component
// ---------------------------------------------------------------------------

export interface ColorPickerProps {
  value: string
  onChange: (hex: string) => void
  className?: string
  "aria-label"?: string
}

export function ColorPicker({ value, onChange, className, "aria-label": ariaLabel }: ColorPickerProps) {
  // Parse incoming hex → internal HSV state
  const parsed = hexToHslComponents(value)
  const initial = parsed ? hslToHsv(parsed.h, parsed.s, parsed.l) : { h: 0, s: 1, v: 1 }

  const [hue, setHue] = React.useState(initial.h)
  const [saturation, setSaturation] = React.useState(initial.s)
  const [brightness, setBrightness] = React.useState(initial.v)
  const [hexInput, setHexInput] = React.useState(value ?? "")

  // Sync external value changes into local state
  React.useEffect(() => {
    if (!value) return
    const p = hexToHslComponents(value)
    if (!p) return
    const hsv = hslToHsv(p.h, p.s, p.l)
    setHue(hsv.h)
    setSaturation(hsv.s)
    setBrightness(hsv.v)
    setHexInput(value)
  }, [value])

  // Compute current hex from HSV state
  const currentHex = React.useMemo(() => {
    const { h, s, l } = hsvToHsl(hue, saturation, brightness)
    return hslToHex(h, s, l)
  }, [hue, saturation, brightness])

  const emitHex = React.useCallback(
    (hex: string) => {
      setHexInput(hex)
      onChange(hex)
    },
    [onChange]
  )

  // Saturation/Brightness field drag
  const handleSbDrag = React.useCallback(
    (clientX: number, clientY: number, rect: DOMRect) => {
      const x = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width))
      const y = Math.max(0, Math.min(1, (clientY - rect.top) / rect.height))
      const newSat = x
      const newBri = 1 - y
      setSaturation(newSat)
      setBrightness(newBri)
      const { h, s, l } = hsvToHsl(hue, newSat, newBri)
      emitHex(hslToHex(h, s, l))
    },
    [hue, emitHex]
  )

  const { startDrag: startSbDrag } = useDrag(handleSbDrag)

  // Hue slider drag
  const handleHueDrag = React.useCallback(
    (clientX: number, _clientY: number, rect: DOMRect) => {
      const x = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width))
      const newHue = x * 360
      setHue(newHue)
      const { h, s, l } = hsvToHsl(newHue, saturation, brightness)
      emitHex(hslToHex(h, s, l))
    },
    [saturation, brightness, emitHex]
  )

  const { startDrag: startHueDrag } = useDrag(handleHueDrag)

  // Hex input handling
  const handleHexChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value
    setHexInput(raw)
    const p = hexToHslComponents(raw)
    if (p) {
      const hsv = hslToHsv(p.h, p.s, p.l)
      setHue(hsv.h)
      setSaturation(hsv.s)
      setBrightness(hsv.v)
      onChange(raw)
    }
  }

  // Pure hue color (full saturation + brightness) for the SB gradient background
  const hueColor = `hsl(${hue}, 100%, 50%)`

  // Cursor position in the SB field (percentage)
  const cursorLeft = `${saturation * 100}%`
  const cursorTop = `${(1 - brightness) * 100}%`

  // Cursor position on hue slider
  const hueLeft = `${(hue / 360) * 100}%`

  return (
    <Popover>
      <PopoverTrigger
        aria-label={ariaLabel}
        className={cn(
          "size-8 shrink-0 cursor-pointer rounded-lg border border-input outline-none transition-shadow focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50",
          className
        )}
        style={{ backgroundColor: value || "#000000" }}
      />
      <PopoverContent className="w-56 p-3 gap-3 flex flex-col" sideOffset={6}>
        {/* Saturation / Brightness field */}
        <div
          role="presentation"
          className="relative h-36 w-full cursor-crosshair select-none overflow-hidden rounded-md"
          style={{
            background: `
              linear-gradient(to bottom, transparent, #000),
              linear-gradient(to right, #fff, ${hueColor})
            `,
          }}
          onMouseDown={startSbDrag}
          onTouchStart={startSbDrag}
        >
          {/* Cursor */}
          <div
            aria-hidden
            className="pointer-events-none absolute size-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white shadow-sm"
            style={{ left: cursorLeft, top: cursorTop }}
          />
        </div>

        {/* Hue slider */}
        <div
          role="presentation"
          className="relative h-3 w-full cursor-ew-resize select-none rounded-full"
          style={{
            background:
              "linear-gradient(to right, #f00, #ff0, #0f0, #0ff, #00f, #f0f, #f00)",
          }}
          onMouseDown={startHueDrag}
          onTouchStart={startHueDrag}
        >
          {/* Thumb */}
          <div
            aria-hidden
            className="pointer-events-none absolute top-1/2 size-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white shadow-sm"
            style={{ left: hueLeft, backgroundColor: hueColor }}
          />
        </div>

        {/* Hex input + swatch preview */}
        <div className="flex items-center gap-2">
          <div
            aria-hidden
            className="size-6 shrink-0 rounded border border-input"
            style={{ backgroundColor: currentHex }}
          />
          <Input
            value={hexInput}
            onChange={handleHexChange}
            placeholder="#000000"
            className="font-mono text-xs h-7"
            maxLength={7}
            spellCheck={false}
          />
        </div>
      </PopoverContent>
    </Popover>
  )
}
