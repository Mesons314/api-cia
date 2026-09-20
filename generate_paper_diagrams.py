import os
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as patches

output_dir = r"c:\Apicia\figures"
os.makedirs(output_dir, exist_ok=True)

# -------------------------------------------------------------------------
# Figure 1: System Architecture Pipeline
# -------------------------------------------------------------------------
def create_fig1_architecture():
    fig, ax = plt.subplots(figsize=(10, 5.5), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    c_source = '#E8F0FE'
    c_ast = '#D2E3FC'
    c_core = '#E6F4EA'
    c_sec = '#FCE8E6'
    c_score = '#FEF7E0'
    c_out = '#F3E8FD'
    
    b_blue = '#1A73E8'
    b_green = '#1E8E3E'
    b_red = '#D93025'
    b_yellow = '#F9AB00'
    b_purple = '#9334E6'

    def draw_box(x, y, w, h, bg, border, title, items, title_color='#202124'):
        rect = patches.FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.5,rounding_size=1.5",
                                      facecolor=bg, edgecolor=border, linewidth=1.5)
        ax.add_patch(rect)
        ax.text(x + w/2, y + h - 4, title, ha='center', va='top', fontsize=9.5, fontweight='bold', color=title_color)
        for i, item in enumerate(items):
            ax.text(x + 2, y + h - 8.5 - (i * 3.5), item, ha='left', va='top', fontsize=7.5, color='#3C4043')

    draw_box(2, 60, 24, 34, c_source, b_blue, "Spring Boot Source", [
        "• REST Controllers (@GetMapping)",
        "• Request/Response DTOs",
        "• Bean Validation Annotations",
        "• Spring Security Configs"
    ])

    draw_box(30, 60, 36, 34, c_ast, b_blue, "Static AST Engine (JavaParser)", [
        "• AST CompilationUnit Traverser",
        "• Recursive DTO Schema Extractor",
        "• Zero-Runtime OpenAPI 3.0 Synthesizer",
        "• Spec Snapshot Versioning Store"
    ])

    draw_box(2, 10, 30, 42, c_core, b_green, "Static Governance (SGM)", [
        "• 11 Breaking Change Rules",
        "  - SGM-003: Removed Endpoint",
        "  - SGM-004: Param Type Change",
        "  - SGM-005: Required Field",
        "  - SGM-006: HTTP Verb Mutation",
        "• Severity Matrix & Weighting",
        "• Structural Distance: D_struct"
    ], title_color='#137333')

    draw_box(35, 10, 30, 42, c_sec, b_red, "Security & Compliance (SAM)", [
        "• Wildcard CORS Over-Permission",
        "• Sensitive PII / Credential Leak",
        "• Unauthenticated Public Routes",
        "• DTO Circular Reference Cycles",
        "• N+1 Query Anti-Pattern Scanner",
        "• Shift-Left Security Alerts"
    ], title_color='#C5221F')

    draw_box(68, 60, 30, 34, c_score, b_yellow, "Blast Radius Engine", [
        "• Client Dependency Registry",
        "• Forward Subscriber Mapping",
        "• Downstream Microservice Slicing",
        "• Bounded Blast Metric: D_blast"
    ], title_color='#B06000')

    draw_box(68, 10, 30, 42, c_out, b_purple, "Multi-Factor Scoring & SDK", [
        "• Total Impact Score (S_total)",
        "  S_total = w1*D_struct + w2*D_blast",
        "• 4-Tier Risk Classification:",
        "  [LOW | MEDIUM | HIGH | CRIT]",
        "• Auto SDK Generator (Java/TS)",
        "• CI/CD Quality Gate Hooks"
    ], title_color='#7627BB')

    arrow_kw = dict(arrowstyle="->", lw=1.5, color="#5F6368")
    ax.annotate("", xy=(30, 77), xytext=(26, 77), arrowprops=arrow_kw)
    ax.annotate("", xy=(17, 52), xytext=(38, 60), arrowprops=arrow_kw)
    ax.annotate("", xy=(50, 52), xytext=(48, 60), arrowprops=arrow_kw)
    ax.annotate("", xy=(68, 77), xytext=(66, 77), arrowprops=arrow_kw)
    ax.annotate("", xy=(68, 30), xytext=(32, 30), arrowprops=arrow_kw)
    ax.annotate("", xy=(68, 25), xytext=(65, 25), arrowprops=arrow_kw)
    ax.annotate("", xy=(83, 52), xytext=(83, 60), arrowprops=arrow_kw)

    plt.title("APICIA End-to-End System Architecture Pipeline", fontsize=11, fontweight='bold', pad=12)
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, "fig1_system_architecture.png"), dpi=300, bbox_inches='tight')
    plt.close()

# -------------------------------------------------------------------------
# Figure 2: Static AST Extraction & Schema Synthesis Workflow
# -------------------------------------------------------------------------
def create_fig2_ast_workflow():
    fig, ax = plt.subplots(figsize=(8.5, 3.8), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 50)
    ax.axis('off')

    steps = [
        ("1. AST Parser", ["CompilationUnit", "Class & Method Trees"], "#E8F0FE", "#1A73E8"),
        ("2. Annotation Visitor", ["@RestController", "@GetMapping, @PostMapping"], "#D2E3FC", "#1A73E8"),
        ("3. DTO Schema Miner", ["Recursive Field Types", "Bean Validation (@NotNull)"], "#E6F4EA", "#1E8E3E"),
        ("4. OpenAPI Generator", ["Operation Models", "OpenAPI 3.0 Document"], "#FEF7E0", "#F9AB00"),
        ("5. Snapshot Store", ["SpecVersion DB Entity", "Hash & Timestamp Track"], "#F3E8FD", "#9334E6")
    ]

    x_positions = [2, 22, 42, 62, 82]
    w, h = 16, 32

    for i, (title, items, bg, border) in enumerate(steps):
        x = x_positions[i]
        rect = patches.FancyBboxPatch((x, 9), w, h, boxstyle="round,pad=0.5,rounding_size=1.2",
                                      facecolor=bg, edgecolor=border, linewidth=1.4)
        ax.add_patch(rect)
        ax.text(x + w/2, 9 + h - 3.5, title, ha='center', va='top', fontsize=8, fontweight='bold', color='#202124')
        for j, item in enumerate(items):
            ax.text(x + 1, 9 + h - 8 - (j * 4.5), item, ha='left', va='top', fontsize=6.8, color='#3C4043')

        if i < len(steps) - 1:
            ax.annotate("", xy=(x_positions[i+1], 25), xytext=(x + w, 25),
                        arrowprops=dict(arrowstyle="->", lw=1.5, color="#5F6368"))

    plt.title("Zero-Runtime Static AST Endpoint & Schema Synthesis Pipeline", fontsize=9.5, fontweight='bold', pad=8)
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, "fig2_ast_workflow.png"), dpi=300, bbox_inches='tight')
    plt.close()

# -------------------------------------------------------------------------
# Figure 3: Blast Radius Consumer Dependency Mapping
# -------------------------------------------------------------------------
def create_fig3_blast_radius():
    fig, ax = plt.subplots(figsize=(7.5, 4.2), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(100)
    ax.axis('off')

    rect_prov = patches.FancyBboxPatch((5, 30), 28, 40, boxstyle="round,pad=0.5,rounding_size=1.5",
                                       facecolor='#E8F0FE', edgecolor='#1A73E8', linewidth=1.5)
    ax.add_patch(rect_prov)
    ax.text(19, 65, "Upstream Provider API", ha='center', va='top', fontsize=8.5, fontweight='bold', color='#1A73E8')
    ax.text(7, 56, "• GET /api/v1/users/{id}", fontsize=7, color='#202124')
    ax.text(7, 49, "• POST /api/v1/orders [M]", fontsize=7, color='#D93025', fontweight='bold')
    ax.text(7, 42, "• DELETE /api/v1/auth [X]", fontsize=7, color='#D93025', fontweight='bold')
    ax.text(7, 35, "• GET /api/v1/products", fontsize=7, color='#202124')

    consumers = [
        ("Mobile Gateway", 80, '#E6F4EA', '#1E8E3E', True),
        ("Web Frontend BFF", 60, '#E6F4EA', '#1E8E3E', True),
        ("Payment Microservice", 40, '#FCE8E6', '#D93025', True),
        ("Notification Worker", 20, '#F1F3F4', '#5F6368', False),
        ("Analytics Reporter", 2, '#F1F3F4', '#5F6368', False)
    ]

    for name, y, bg, border, is_impacted in consumers:
        rect_c = patches.FancyBboxPatch((65, y), 30, 14, boxstyle="round,pad=0.5,rounding_size=1.2",
                                        facecolor=bg, edgecolor=border, linewidth=1.4)
        ax.add_patch(rect_c)
        status = " [IMPACTED]" if is_impacted else " [UNAFFECTED]"
        title_c = '#C5221F' if is_impacted else '#3C4043'
        ax.text(80, y + 10, name, ha='center', va='top', fontsize=7.5, fontweight='bold', color=title_c)
        ax.text(80, y + 4.5, "Subscribed: /orders, /auth" if is_impacted else "Subscribed: /products",
                ha='center', va='top', fontsize=6.5, color='#5F6368')

        line_color = '#D93025' if is_impacted else '#BDC1C6'
        line_style = '-' if is_impacted else '--'
        ax.plot([33, 65], [50, y + 7], color=line_color, linestyle=line_style, lw=1.5 if is_impacted else 1.0)

    ax.text(50, 95, "Blast Radius Forward Reachability Analysis (D_blast = 0.60)",
            ha='center', va='top', fontsize=8.5, fontweight='bold', color='#202124')

    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, "fig3_blast_radius.png"), dpi=300, bbox_inches='tight')
    plt.close()

# -------------------------------------------------------------------------
# Figure 4: Multi-Factor Scoring & Continuous Risk Classification Model
# -------------------------------------------------------------------------
def create_fig4_scoring_model():
    fig, ax = plt.subplots(figsize=(8.0, 3.8), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 60)
    ax.axis('off')

    # Formula Box
    rect_form = patches.FancyBboxPatch((5, 38), 90, 18, boxstyle="round,pad=0.5,rounding_size=1.2",
                                       facecolor='#F8F9FA', edgecolor='#5F6368', linewidth=1.2)
    ax.add_patch(rect_form)
    ax.text(50, 51, r"$S_{total} = \min\left(1.0,\; (w_1 \cdot D_{struct}) + (w_2 \cdot D_{blast})\right)$",
            ha='center', va='center', fontsize=10.5, fontweight='bold', color='#202124')
    ax.text(50, 42, r"Default Configuration: $w_1 = 0.70$ (Structural Breaking Weight), $w_2 = 0.30$ (Consumer Blast Weight)",
            ha='center', va='center', fontsize=7.5, color='#5F6368')

    # 4 Tiers Scale
    tiers = [
        ("LOW", "0.00 <= S < 0.25", "Auto-Approve / Merge", "#E6F4EA", "#1E8E3E", '#137333'),
        ("MEDIUM", "0.25 <= S < 0.50", "Non-Blocking PR Warning", "#FEF7E0", "#F9AB00", '#B06000'),
        ("HIGH", "0.50 <= S < 0.75", "Architect Review Required", "#FEEFC3", "#E37400", '#C53929'),
        ("CRITICAL", "0.75 <= S <= 1.00", "Build Break / Gate Failure", "#FCE8E6", "#D93025", '#C5221F')
    ]

    w_tier = 21
    gap = 2.5
    for i, (name, range_txt, action, bg, border, txt_c) in enumerate(tiers):
        x = 5 + i * (w_tier + gap)
        rect = patches.FancyBboxPatch((x, 5), w_tier, 26, boxstyle="round,pad=0.5,rounding_size=1.2",
                                      facecolor=bg, edgecolor=border, linewidth=1.4)
        ax.add_patch(rect)
        ax.text(x + w_tier/2, 26, name, ha='center', va='top', fontsize=8.5, fontweight='bold', color=txt_c)
        ax.text(x + w_tier/2, 19, range_txt, ha='center', va='top', fontsize=6.8, color='#3C4043')
        ax.text(x + w_tier/2, 11, action, ha='center', va='top', fontsize=6.5, fontweight='bold', color=txt_c)

    plt.title("Multi-Factor Risk Classification Model & CI/CD Gate Policies", fontsize=9.5, fontweight='bold', pad=8)
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, "fig4_latency_benchmark.png"), dpi=300, bbox_inches='tight')
    plt.close()

# -------------------------------------------------------------------------
# Figure 5: End-to-End Case Study Execution Trace
# -------------------------------------------------------------------------
def create_fig5_case_study_trace():
    fig, ax = plt.subplots(figsize=(8.5, 4.2), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    stages = [
        ("Step 1: Ingestion", ["OpenAPI v1.0 (Old)", "OpenAPI v2.0 (New)"], 5, '#E8F0FE', '#1A73E8'),
        ("Step 2: SGM & SAM", ["• SGM-003 (EP Deleted)", "• SGM-004 (Type Drift)", "• CORS Wildcard Alert"], 28, '#E6F4EA', '#1E8E3E'),
        ("Step 3: Reachability", ["• Blast Radius Scan", "• 3 Consumers Mapped", "• D_blast = 0.30"], 52, '#FEF7E0', '#F9AB00'),
        ("Step 4: Decision & SDK", ["• S_total = 0.58 (HIGH)", "• Trigger Review Gate", "• Auto-Gen Client SDK"], 75, '#F3E8FD', '#9334E6')
    ]

    for title, items, x, bg, border in stages:
        rect = patches.FancyBboxPatch((x, 15), 20, 68, boxstyle="round,pad=0.5,rounding_size=1.2",
                                      facecolor=bg, edgecolor=border, linewidth=1.4)
        ax.add_patch(rect)
        ax.text(x + 10, 77, title, ha='center', va='top', fontsize=8, fontweight='bold', color='#202124')
        for j, item in enumerate(items):
            ax.text(x + 1.5, 66 - (j * 11), item, ha='left', va='top', fontsize=6.8, color='#3C4043')

    # Connective arrows
    ax.annotate("", xy=(28, 50), xytext=(25, 50), arrowprops=dict(arrowstyle="->", lw=1.5, color="#5F6368"))
    ax.annotate("", xy=(52, 50), xytext=(48, 50), arrowprops=dict(arrowstyle="->", lw=1.5, color="#5F6368"))
    ax.annotate("", xy=(75, 50), xytext=(72, 50), arrowprops=dict(arrowstyle="->", lw=1.5, color="#5F6368"))

    plt.title("End-to-End API Evolution Governance & Mitigation Execution Trace", fontsize=9.5, fontweight='bold', pad=8)
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, "fig5_sgm_accuracy.png"), dpi=300, bbox_inches='tight')
    plt.close()

if __name__ == '__main__':
    print("Generating qualitative methodology & architecture diagrams...")
    create_fig1_architecture()
    create_fig2_ast_workflow()
    create_fig3_blast_radius()
    create_fig4_scoring_model()
    create_fig5_case_study_trace()
    print("All figures successfully updated in", output_dir)
