.global nop_x86_beg
.global nop_x86_end
.global fault_x86_beg
.global fault_x86_hit
.global fault_x86_end

nop_x86_beg:
	nop
nop_x86_end:

fault_x86_beg:
	mov esp , eax
	inc eax
fault_x86_hit:
	movntps xmm0 , eax
	ret
fault_x86_end:
	nop